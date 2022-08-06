package com.nest.ib.service.serviceImpl;

import com.nest.ib.config.NestProperties;
import com.nest.ib.constant.Constant;
import com.nest.ib.contract.PriceSheetView;
import com.nest.ib.helper.WalletHelper;
import com.nest.ib.state.GasPriceState;
import com.nest.ib.utils.EthClient;
import com.nest.ib.model.*;
import com.nest.ib.service.*;
import com.nest.ib.state.Erc20State;
import com.nest.ib.state.VerifyState;
import com.nest.ib.utils.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

@Service
public class BiteServiceImpl implements BiteService {
    private static final Logger log = LoggerFactory.getLogger(BiteServiceImpl.class);

    private ExecutorService executorService = new ThreadPoolExecutor(5, 5,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(5));

    private static final String BITE_TOKEN0 = "takeToken0";
    private static final String BITE_TOKEN1 = "takeToken1";

    @Autowired
    private EthClient ethClient;
    @Autowired
    private Erc20State erc20State;
    @Autowired
    private VerifyState verifyState;
    @Autowired
    private WalletHelper walletHelper;
    @Autowired
    private GasPriceState gasPriceState;
    @Autowired
    private NestProperties nestProperties;

    @Autowired(required = false)
    private HedgeService hedgeService;

    private static BigInteger NONCE = null;

    @Override
    public void bite(Wallet wallet) {
        // Check that validation is enabled
        if (!verifyState.isOpen()) {
            log.info("Validation is not enabled");
            return;
        }

        String address = wallet.getCredentials().getAddress();
        NONCE = ethClient.ethGetTransactionCount(address);
        if (NONCE == null) {
            log.error("Failed to get nonce during validation");
            return;
        }

        List<PriceSheetView> tokenSheetPubList = ethClient.unVerifiedSheetList(erc20State.channelId);

        if (CollectionUtils.isEmpty(tokenSheetPubList)) {
            log.info("No quotation pending verification");
            return;
        }

        // Get the current block number and verify again that the validation period has expired
        BigInteger nowBlockNumber = ethClient.ethBlockNumber();
        if (nowBlockNumber == null) {
            log.info("Failed to get the latest block number during validation");
            return;
        }

        // Update the balance
        if (!walletHelper.updateBalance(wallet, false)) return;

        boolean bite = false;
        // Perform token quotation verification
        if (!CollectionUtils.isEmpty(tokenSheetPubList)) {
            Erc20State.Item erc20 = erc20State.token;
            bite = bite(wallet, tokenSheetPubList, nowBlockNumber, erc20, verifyState.getTokenBiteThreshold());
        }

        if (bite) {
            // An order eating transaction is initiated, where it sleeps and waits for the transaction to be packaged before the next round of validation
            try {
                Thread.sleep(60 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private boolean bite(Wallet wallet,
                         List<PriceSheetView> sheetPubList,
                         BigInteger nowBlockNumber,
                         Erc20State.Item erc20,
                         BigDecimal biteRate) {
        boolean bite = false;
        for (PriceSheetView priceSheetPub : sheetPubList) {
            if (erc20.haveEaten(priceSheetPub.index)) {
                log.info("[{}]:This quotation has been verified in the last round", priceSheetPub.index);
                continue;
            }

            // Based on remainNum, how many ETH/TOKEN remainsto be eaten, and once it's eaten, it's going to go down to 0, and then the offer can't be eaten
            if (priceSheetPub.remainNum.compareTo(BigInteger.ZERO) == 0) {
                continue;
            }
            // The validation period was judged again
            BigInteger subtract = nowBlockNumber.subtract(priceSheetPub.height);
            if (subtract.compareTo(nestProperties.getPriceDurationBlock()) > 0) continue;

            // Get this quotation when eating order double token
            BigInteger tokenMultiple = priceSheetPub.level.intValue() <= nestProperties.getMaxBiteNestedLevel() ? nestProperties.getBiteInflateFactor() : BigInteger.ONE;
            // The price of this quotation list
            BigDecimal orderPrice = MathUtils.intDivDec(priceSheetPub.price, erc20State.token1.getDecPowTen(), erc20State.token1.getDecimals()).divide(erc20State.unit, 18, BigDecimal.ROUND_DOWN);

            // Quantity of Remainable token0: remainNum * Base Quote Size
            BigInteger dealToken0Amount = priceSheetPub.remainNum.multiply(erc20State.getNeedToken0());
            // Number of remaining token1 transactions
            BigInteger dealToken1Amount = priceSheetPub.remainNum.multiply(priceSheetPub.price);
            log.info("{}[{}]  The remaining {} that can be traded：{} ,The remaining {} that can be traded：{}", erc20.getSymbol(), priceSheetPub.index, erc20State.token.getSymbol(), dealToken0Amount, erc20State.token1.getSymbol(), dealToken1Amount);

            if (!erc20State.updateToken0Token1Price()) continue;

            // Judge whether the quotation contract meets the conditions of eating orders: there is surplus, profitable
            boolean meetBiteCondition = meetBiteCondition(orderPrice, erc20State.token0Token1Pirce, biteRate);
            if (!meetBiteCondition) continue;

            // Determine the type of eating order
            boolean biteToken0 = false;
            // The exchange price is greater than the price of the order to be eaten: pay ERC20, eat ETH, and then the exchange sells ETH at a higher price
            if (erc20State.token0Token1Pirce.compareTo(orderPrice) > 0) {
                biteToken0 = true;
            } else { // The exchange price is less than the price of the eaten order: pay the EHT, eat the ERC20, and then the exchange buys the ETH at a lower price
                biteToken0 = false;
            }

            // Determine if you can eat it all
            BigInteger biteAllFee = MathUtils.toDecimal(dealToken0Amount).multiply(nestProperties.getBiteFeeRate()).multiply(Constant.UNIT_ETH).toBigInteger();
            boolean canEatAll = canEatAll(wallet, tokenMultiple, biteToken0, dealToken0Amount, dealToken1Amount, biteAllFee, priceSheetPub.nestNum1k);
            BigInteger copies = null;
            // You can eat all of them
            BigInteger biteFee = null;
            if (canEatAll) {
                copies = dealToken0Amount.divide(erc20State.getNeedToken0());
                biteFee = biteAllFee;
            } else {// You can't eat it all
                // Number of single serving: the base quotation size is one serving
                biteFee = MathUtils.toDecimal(erc20State.getNeedToken0()).multiply(nestProperties.getBiteFeeRate()).toBigInteger();
                BigInteger beginNum = (priceSheetPub.ethNumBal.add(priceSheetPub.tokenNumBal)).divide(Constant.BIG_INTEGER_TWO);
                copies = getCopies(wallet.getUseable(), biteToken0, orderPrice, tokenMultiple, priceSheetPub.nestNum1k, beginNum);
                if (copies.compareTo(BigInteger.ZERO) <= 0) {
                    log.error("The balance is not enough to eat the order");
                    return false;
                }
            }

            // By eating order
            String hash = sendBiteOffer(priceSheetPub, tokenMultiple, biteToken0, copies, NONCE, biteFee, wallet);
            if (!StringUtils.isEmpty(hash)) {
                NONCE = NONCE.add(BigInteger.ONE);
                erc20.addBiteIndex(priceSheetPub.index);
                bite = true;
            }

        }
        return bite;
    }

    private String sendBiteOffer(PriceSheetView priceSheetPub,
                                 BigInteger multiple,
                                 boolean takeToken0,
                                 BigInteger copies,
                                 BigInteger nonce,
                                 BigInteger biteFee,
                                 Wallet wallet) {

        // Base quotation scale
        BigInteger miningUnit = BigInteger.ONE;
        BigInteger biteNum = copies.multiply(miningUnit);
        BigInteger newTokenAmountPerEth = erc20State.token0Token1Pirce.multiply(erc20State.unit).multiply(erc20State.token1.getDecPowTen()).toBigInteger();
        BigInteger index = priceSheetPub.index;

        // Amount of ETH to be entered into the contract: service fee + to be transferred from the account
        BigInteger payEthAmount = BigInteger.ZERO;
        // The number of token0 required to eat the order
        BigInteger needToken0 = null;
        // The number of token1 required to eat the order
        BigInteger needToken1 = null;
        // Number of ERC20 that can be traded
        BigInteger tranErc20Amount = priceSheetPub.price.multiply(biteNum);

        String msg = null;
        String method = null;

        if (takeToken0) {
            msg = "takeToken0 Eat order (enter {} to get {}) , Hash ： {}";
            method = BITE_TOKEN0;

            needToken0 = biteNum.multiply(multiple).subtract(biteNum);
            needToken1 = newTokenAmountPerEth.multiply(biteNum).add(tranErc20Amount);
        } else {
            msg = "takeToken1 Eat order (enter {} to get {}) Hash ： {}";
            method = BITE_TOKEN1;

            needToken0 = biteNum.multiply(multiple).add(biteNum);
            needToken1 = newTokenAmountPerEth.multiply(biteNum).subtract(tranErc20Amount);
        }

        List<Type> typeList = Arrays.<Type>asList(
                new Uint256(erc20State.channelId),
                new Uint256(index),
                new Uint256(biteNum),
                new Uint256(newTokenAmountPerEth)
        );

        needToken0 = needToken0.multiply(erc20State.token.getDecPowTen().toBigInteger());
        if (erc20State.token.getZero()) {
            payEthAmount = needToken0.add(biteFee);
        }
        if (erc20State.token1.getZero()) {
            payEthAmount = needToken1.add(biteFee);
        }

        BigInteger gasPrice = ethClient.ethGasPrice(gasPriceState.baseBiteType);
        String minerAddress = priceSheetPub.miner.getValue();
        String transactionHash = ethClient.bite(method, wallet, gasPrice, nonce, typeList, payEthAmount);

        log.info(msg, erc20State.token.getSymbol(), erc20State.token1.getSymbol(), transactionHash);

        // hedge
        if (!StringUtils.isEmpty(transactionHash) && verifyState.isHedge()) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    if (!ethClient.checkTxStatus(transactionHash, nonce, wallet.getCredentials().getAddress())) {
                        return;
                    }
                    if (minerAddress.equalsIgnoreCase(wallet.getCredentials().getAddress())) {
                        log.info("Eat your own quotation and don't hedge");
                        return;
                    }
                    BigDecimal token0Amount = MathUtils.decMulInt(erc20State.unit, biteNum);
                    BigDecimal token1Amount = MathUtils.intDivDec(tranErc20Amount, erc20State.token1.getDecPowTen(), erc20State.token1.getDecimals());
                    log.info("To hedge");
                    if (takeToken0) {
                        // Sell token0Amount token0 or buy token1Amount token1
                        hedgeService.sellToken0(token0Amount);
                    } else {
                        // Buy token0Amount token0 or sell token1Amount token1
                        hedgeService.sellToken1(token1Amount);
                    }
                }
            });
        }

        return transactionHash;
    }

    private BigInteger getCopies(Wallet.Asset asset,
                                 boolean biteToken0,
                                 BigDecimal orderPrice,
                                 BigInteger multiple,
                                 BigInteger nestNum1k,
                                 BigInteger ethNum) {


        BigDecimal exchangePrice = erc20State.token0Token1Pirce;
        // The amount of token0 required to eat a serving size
        BigInteger offerToken0 = multiple.multiply(erc20State.getNeedToken0());
        BigDecimal offerToken0Unit = MathUtils.intDivDec(offerToken0, erc20State.token.getDecPowTen(), 18);
        // Eat the number of token1 required for a serving size
        BigInteger offerToken1 = exchangePrice.multiply(offerToken0Unit).multiply(erc20State.token1.getDecPowTen()).toBigInteger();
        // The amount of token0 paid to the other party in a single order
        BigInteger eatToken0 = orderPrice.multiply(erc20State.unit).multiply(erc20State.token1.getDecPowTen()).toBigInteger();

        // It takes the total number of token0 to eat one serving
        BigInteger eatOneToken0 = null;
        // It takes a total of token1 to eat one serving
        BigInteger eatOneToken1 = null;
        // Eat a portion of Nest that requires collateral
        BigInteger biteEthNum = BigInteger.ONE;
        BigInteger newNestNum1k = nestNum1k.multiply(nestProperties.getBiteNestInflateFactor().multiply(biteEthNum)).divide(ethNum);
        BigInteger needNest = newNestNum1k.multiply(Constant.BIG_INTEGER_1K).multiply(Constant.UNIT_INT18);

        if (biteToken0) {
            eatOneToken0 = offerToken0.subtract(erc20State.getNeedToken0());
            eatOneToken1 = offerToken1.add(eatToken0);
        } else {
            eatOneToken0 = offerToken0.add(erc20State.getNeedToken0());
            eatOneToken1 = offerToken1.subtract(eatToken0);
        }

        if (erc20State.token1.getSymbol().equalsIgnoreCase("NEST")) {
            // The number of Nest eating order, Ntoken needs to be plus the number of Nest mortgaged
            eatOneToken1 = eatOneToken1.add(needNest);
            needNest = eatOneToken1;
        }

        // The balance of ETH can be eaten in a single serving
        BigInteger copiesToken0 = MathUtils.intDivInt(asset.getTokenAmount(), eatOneToken0, 0).toBigInteger();
        // ERC20 balance can be eaten in single servings
        BigInteger copiesToken1 = MathUtils.intDivInt(asset.getToken1Amount(), eatOneToken1, 0).toBigInteger();

        if (eatOneToken1.compareTo(BigInteger.ZERO) < 0) {
            // ERC20 is not required for the order and will return ERC20, so ERC20 is sufficient and is set to 10 by default
            copiesToken1 = BigInteger.TEN;
        }

        BigInteger copiseNest = MathUtils.intDivInt(asset.getNestAmount(), needNest, 0).toBigInteger();

        log.info("{} servings available: {}", erc20State.token.getSymbol(), copiesToken0);
        log.info("{} servings available: {}", erc20State.token1.getSymbol(), copiesToken1);
        log.info("NEST servings available: {}", copiseNest);

        if (copiesToken1.compareTo(copiesToken0) < 0) {
            if (copiesToken1.compareTo(copiseNest) < 0) {
                return copiesToken1;
            }
            return copiseNest;
        }
        if (copiesToken0.compareTo(copiseNest) < 0) return copiesToken0;
        return copiseNest;
    }

    private boolean canEatAll(Wallet wallet, BigInteger multiple,
                              boolean biteToken0,
                              BigInteger dealToken0Amount,
                              BigInteger dealToken1Amount,
                              BigInteger biteFee,
                              BigInteger nestNum1k) {
        BigDecimal token0Token1Pirce = erc20State.token0Token1Pirce;
        // Eat all quoted ETH quantity
        BigInteger token0Amount = multiple.multiply(dealToken0Amount);
        // Eat the full quotation ERC20 quantity
        BigInteger token0 = MathUtils.toBigInt(MathUtils.toDecimal(token0Amount).divide(erc20State.token.getDecPowTen(), 0, BigDecimal.ROUND_DOWN));
        BigInteger token1Amount = MathUtils.decMulInt(token0Token1Pirce, token0).multiply(erc20State.token1.getDecPowTen()).toBigInteger();

        // Eat all the minimum amount of token0 required
        BigInteger minToken0Amount = null;
        // Eat all the minimum required token1
        BigInteger minToken1Amount = null;

        // Calculate the number of NEST mortgage required for this order
        BigInteger biteToken0Num = dealToken0Amount.divide(erc20State.token.getDecPowTen().toBigInteger());// Eat all
        BigInteger newNestNum1k = nestNum1k.multiply(nestProperties.getBiteNestInflateFactor().multiply(biteToken0Num)).divide(biteToken0Num);
        BigInteger needNest = newNestNum1k.multiply(Constant.BIG_INTEGER_1K);

        if (biteToken0) {
            // The minimum amount of token0 required to eat all: the quoted amount + the charge for eating the order  - the amount of token0 obtained by eating the order
            minToken0Amount = token0Amount.add(biteFee).subtract(dealToken0Amount);
            // Minimum number of ERC20 required to eat all: QUOTE ERC20+ ERC20 required to eat order
            minToken1Amount = token1Amount.add(dealToken1Amount);
        } else {
            // The minimum amount of token0 required to eat all: the quoted amount + the cost of eating the order +  the amount of token0 required to eat the order
            minToken0Amount = token0Amount.add(biteFee).add(dealToken0Amount);
            // Minimum number of ERC20 required to eat all: token0* exchange price - number of ERC20 obtained by eating order
            minToken1Amount = token1Amount.subtract(dealToken1Amount);
        }

        if (erc20State.token1.getSymbol().equalsIgnoreCase("NEST")) {
            // The number of Nest eating single Ntoken needs to be plus the number of Nest mortgaged
            minToken1Amount = minToken1Amount.add(needNest);
            needNest = minToken1Amount;
        }

        // You can't eat it all
        if (wallet.getUseable().getTokenAmount().compareTo(minToken0Amount) < 0) {
            log.info("Available {} balance = {}, eat all need at least {}, can not eat all", erc20State.token.getSymbol(), wallet.getUseable().getTokenAmount(), minToken0Amount);
            return false;
        }

        if (wallet.getUseable().getToken1Amount().compareTo(minToken1Amount) < 0) {
            log.info("Available {} balance = {}, eat all need at least {}, can not eat all", erc20State.token.getSymbol(), wallet.getUseable().getTokenAmount(), minToken1Amount);
            return false;
        }

        // Determine if the NEST balance is sufficient
        if (wallet.getUseable().getNestAmount().compareTo(needNest) < 0) {
            log.info("Available NEST balance = {}, eat all at least {} NEST, can not eat all", wallet.getUseable().getNestAmount(), needNest);
            return false;
        }
        // Can eat all
        return true;
    }

    // Determine whether it meets the conditions for eating the order
    private boolean meetBiteCondition(BigDecimal orderPrice,
                                      BigDecimal exchangePrice,
                                      BigDecimal biteRate) {
        if (exchangePrice == null) {
            log.error("Exchange price acquisition failed, unable to eat orders");
            return false;
        }
        // Calculate price deviation
        BigDecimal priceDeviation = (orderPrice.subtract(exchangePrice)).divide(exchangePrice, 10, BigDecimal.ROUND_DOWN).abs();

        log.info("Quotation deviation: {}, eat order threshold: {}", priceDeviation, biteRate);
        // Less than the threshold value of eating order, do not eat order
        if (priceDeviation.compareTo(biteRate) < 0) {
            return false;
        }

        return true;
    }

    /**
     * Unfreeze assets in bulk
     *
     * @param wallet
     */
    @Override
    public void closePriceSheets(Wallet wallet) {
        String address = wallet.getCredentials().getAddress();
        BigInteger nonce = ethClient.ethGetTransactionCount(address);
        if (nonce == null) {
            log.error("{} ：closeList Failed to get nonce", address);
            return;
        }
        if (walletHelper.updateBalance(wallet, false)) {
            closePriceSheetList(wallet, nonce);
        }
    }

    private String closePriceSheetList(Wallet wallet, BigInteger nonce) {
        // Get quotes that can be defrosted
        List<Uint256> indices = ethClient.canClosedSheetIndexs(wallet.getCredentials().getAddress(), erc20State.channelId, verifyState.getMaxFindNum());

        boolean empty = CollectionUtils.isEmpty(indices);
        if (empty) return null;

        // Whether to wait for the next thaw
        boolean wait = true;
        int size = empty ? 0 : indices.size();
        if (empty || size < verifyState.getCloseMinNum()) {
            log.info("Quantity of quotation sheets that can be thawed :{}, the minimum thawed quantity is not reached :{}", size, verifyState.getCloseMinNum());

            // Gets the largest index of the current quotation list
            BigInteger nowMaxIndex = ethClient.lastIndex(erc20State.channelId);
            if (nowMaxIndex == null) {
                wait = false;
                log.info("Quotation list length retrieval failed. Must be defrosted");
            }

            if (size > 0 && wait) {
                BigInteger farthestIndex = indices.get(size - 1).getValue();
                // Maximum number of queries -10
                BigInteger subtract = verifyState.getMaxFindNum().subtract(BigInteger.TEN);
                // The largest index of the current quotation list - the index of the last quotation that can be defrosted
                BigInteger subtract1 = nowMaxIndex.subtract(farthestIndex);
                if (subtract1.compareTo(subtract) >= 0) {
                    // If the difference between the largest index of the current quotation list and the index of the last quotation list is greater than the maximum number of queries -10,
                    // it must be defrosted at this time to avoid that the quotation list cannot be defrosted by subsequent queries
                    wait = false;
                    log.info("This quotation is so old that it must be defrosted");
                }
            }

            if (wait) {
                // Check whether the remaining assets can be eaten
                boolean biteEth = true;
                BigInteger copies1 = getCopies(wallet.getUseable(), biteEth, erc20State.token0Token1Pirce, Constant.BIG_INTEGER_TWO, Constant.BIG_INTEGER_100, BigInteger.ONE);
                if (copies1.compareTo(BigInteger.ONE) < 0) {
                    wait = false;
                    log.info("The available assets of the account are insufficient, so it is no longer possible to offer orders. At this time, assets must be unfrozen");
                }
            }
        }

        if (size >= verifyState.getCloseMinNum()) wait = false;

        if (wait) return null;

        String close = ethClient.close(wallet, nonce, indices, erc20State.channelId);
        log.info("Close hash : {}", close);
        return close;
    }

}
