package com.nest.ib.utils;

import com.nest.ib.config.NestProperties;
import com.nest.ib.constant.Constant;
import com.nest.ib.constant.GasLimit;
import com.nest.ib.contract.*;
import com.nest.ib.model.Wallet;
import com.nest.ib.helper.Web3jHelper;
import com.nest.ib.state.Erc20State;
import com.nest.ib.state.GasPriceState;
import com.nest.ib.state.VerifyState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple2;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutionException;


/**
 * @author wll
 * @date 2020/7/16 13:25
 */
@Component
public class EthClient {
    private static final Logger log = LoggerFactory.getLogger(EthClient.class);

    @Autowired
    private Erc20State erc20State;
    @Autowired
    private VerifyState verifyState;
    @Autowired
    private GasPriceState gasPriceState;
    @Autowired
    private NestProperties nestProperties;

    public EthGetTransactionReceipt ethGetTransactionReceipt(String hash) {
        EthGetTransactionReceipt transactionReceipt = null;
        try {
            transactionReceipt = Web3jHelper.getWeb3j().ethGetTransactionReceipt(hash).send();
        } catch (IOException e) {
            log.error("ethGetTransactionReceipt error：{}", e.getMessage());
        }
        return transactionReceipt;
    }

    /**
     * Check to see if one-time authorization has taken place, and if not, one-time authorization has taken place
     */
    public void approveToNestMinningContract(Wallet wallet) throws Exception {
        log.info("Authorization checking");
        BigInteger nonce = ethGetTransactionCount(wallet.getCredentials().getAddress());
        if (nonce == null) {
            log.error("Failed to get nonce while authorizing : {}", nonce);
            throw new Exception("Failed to get nonce while authorizing");
        }
        // Authorization of the token
        String tokenApproveHash = erc20Appprove(wallet, erc20State.token, nonce);
        nonce = StringUtils.isEmpty(tokenApproveHash) ? nonce : nonce.add(BigInteger.ONE);
        // Authorization of the nToken
        String nTokenApproveHash = erc20Appprove(wallet, erc20State.token1, nonce);

        // If the token is not a USDT, NEST also needs to be authorized
        if (!erc20State.token.getSymbol().equalsIgnoreCase("USDT")) {
            nonce = StringUtils.isEmpty(nTokenApproveHash) ? nonce : nonce.add(BigInteger.ONE);
            Erc20State.Item nest = new Erc20State.Item();
            nest.setAddress(nestProperties.getNestTokenAddress());
            nest.setSymbol("NEST");
            String nestApproveHash = erc20Appprove(wallet, nest, nonce);
        }
    }

    public String erc20Appprove(Wallet wallet, Erc20State.Item token, BigInteger nonce) throws ExecutionException, InterruptedException {
        String transactionHash = null;
        BigInteger gasPrice = ethGasPrice(gasPriceState.approveType);
        if (gasPrice == null) throw new NullPointerException("Failed to get GASPrice during authorization check");

        BigInteger approveValue = allowance(wallet, token.getAddress());
        if (approveValue == null) {
            log.error("Failed to get approveValue while authorizing: {}", approveValue);
        }

        if (approveValue.compareTo(new BigInteger("100000000000000")) <= 0) {
            List<Type> typeList = Arrays.<Type>asList(
                    new Address(nestProperties.getNestMiningAddress()),
                    new Uint256(new BigInteger("999999999999999999999999999999999999999999"))
            );
            Function function = new Function("approve", typeList, Collections.<TypeReference<?>>emptyList());
            String encode = FunctionEncoder.encode(function);
            BigInteger payableEth = BigInteger.ZERO;

            RawTransaction tokenRawTransaction = RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    GasLimit.APPROVE_GAS_LIMIT,
                    token.getAddress(),
                    payableEth,
                    encode);

            EthSendTransaction ethSendTransaction = ethSendRawTransaction(wallet.getCredentials(), tokenRawTransaction);
            if (ethSendTransaction.hasError()) {
                Response.Error error = ethSendTransaction.getError();
                log.error("Authorized transaction return failed:[msg = {}],[data = {}],[code = {}],[result = {}],[RawResponse = {}],",
                        error.getMessage(),
                        error.getData(),
                        error.getCode(),
                        ethSendTransaction.getResult(),
                        ethSendTransaction.getRawResponse());
            } else {
                transactionHash = ethSendTransaction.getTransactionHash();
                log.info("{} : {} one-time authorization hash: {} ", wallet.getCredentials().getAddress(), token.getSymbol(), transactionHash);
            }
        }
        return transactionHash;
    }

    public boolean checkTxStatus(String txHash, BigInteger nonce, String minnerAddress) {

        if (StringUtils.isEmpty(txHash)) {
            log.error("The transaction hash is empty, stop detecting the transaction status!");
            return false;
        }

        if (!checkNonce(nonce, minnerAddress)) {
            log.error(String.format("Current transaction exception, hash: %s", txHash));
            return false;
        }

        log.info(String.format("Check the transaction status hash: %s", txHash));
        Optional<TransactionReceipt> transactionReceipt = ethGetTransactionReceipt(txHash).getTransactionReceipt();
        if (transactionReceipt == null) return false;
        if (!transactionReceipt.isPresent()) {
            log.error(String.format("The transaction has been overwritten, %s", txHash));
            return false;
        }

        int status = Integer.parseInt(transactionReceipt.get().getStatus().substring(2));
        return status == 1;
    }

    private boolean checkNonce(BigInteger nonce, String address) {
        BigInteger transactionCount = ethGetTransactionCount(address);

        if (nonce == null) return checkNonce(nonce, address);
        if (nonce.compareTo(transactionCount) < 0) {
            log.info("Trading nonce has changed");
            return true;
        }

        log.info("The transaction is being packaged, check the transaction status again in 3 seconds!");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return checkNonce(nonce, address);
    }

    public BigInteger balanceOfItem(Erc20State.Item item, String user) {
        if (item.getZero()) return ethGetBalance(user);
        return ethBalanceOfErc20(user, item.getAddress());
    }

    public BigInteger itemBalanceOfInContract(Erc20State.Item item, String miner) {
        if (item.getZero()) return BigInteger.ZERO;
        return balanceOfInContract(item.getAddress(), miner);
    }

    public BigInteger totalSupply(String erc20Address) {
        BigInteger totalSupply = null;

        try {
            totalSupply = ContractBuilder.erc20Readonly(erc20Address, Web3jHelper.getWeb3j()).totalSupply().send();
        } catch (Exception e) {
            log.error("Total issuance of substitutable coins failed :{}", e.getMessage());
        }

        return totalSupply;
    }


    public boolean withdraw(String tokenAddress, BigInteger tokenAmount, Wallet wallet) {
        BigInteger gasPrice = ethGasPrice(gasPriceState.withdrawType);
        BigInteger nonce = ethGetTransactionCount(wallet.getCredentials().getAddress());
        if (gasPrice == null || nonce == null) {
            log.info("GasPrice | | nonce for failure");
            return false;
        }

        List<Type> typeList = Arrays.<Type>asList(
                new Address(tokenAddress),
                new Uint256(tokenAmount));

        Function function = new Function("withdraw", typeList, Collections.<TypeReference<?>>emptyList());
        String encode = FunctionEncoder.encode(function);
        String transaction = null;
        try {
            RawTransaction rawTransaction = RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    GasLimit.DEFAULT_GAS_LIMIT,
                    nestProperties.getNestMiningAddress(),
                    BigInteger.ZERO,
                    encode);
            EthSendTransaction ethSendTransaction = ethSendRawTransaction(wallet.getCredentials(), rawTransaction);
            transaction = ethSendTransaction.getTransactionHash();
            if (ethSendTransaction.hasError()) {
                Response.Error error = ethSendTransaction.getError();
                log.error("Withdraw transaction return failed：[msg = {}],[data = {}],[code = {}],[result = {}],[RawResponse = {}],",
                        error.getMessage(),
                        error.getData(),
                        error.getCode(),
                        ethSendTransaction.getResult(),
                        ethSendTransaction.getRawResponse());
            }
            log.info("withdraw hash :{}", transaction);
        } catch (Exception e) {
            log.error("Send withdraw transaction failed：{}", e.getMessage());
            return false;
        }
        return true;
    }


    public BigInteger ethGasPrice(GasPriceState.Type type) {
        BigDecimal gasPriceMul = type.getGasPriceMul();
        BigInteger gasPrice = null;

        try {
            gasPrice = Web3jHelper.getWeb3j().ethGasPrice().send().getGasPrice();
        } catch (IOException e) {
            log.error("Failed to get GasPrice: {}", e.getMessage());
        }
        if (gasPrice == null) return gasPrice;
        gasPrice = MathUtils.toDecimal(gasPrice).multiply(gasPriceMul).toBigInteger();

        return gasPrice;
    }

    public BigInteger allowance(Wallet wallet, String erc20TokenAddress) {
        BigInteger approveValue = null;
        try {
            approveValue = ContractBuilder.erc20Readonly(erc20TokenAddress, Web3jHelper.getWeb3j()).allowance(wallet.getCredentials().getAddress(), nestProperties.getNestMiningAddress()).sendAsync().get();
        } catch (InterruptedException e) {
            log.error("Failed to query authorization amount: {}", e.getMessage());
        } catch (ExecutionException e) {
            log.error("Failed to query authorization amount: {}", e.getMessage());
        }
        return approveValue;
    }

    public BigInteger ethGetTransactionCount(String address) {
        BigInteger transactionCount = null;

        try {
            transactionCount = Web3jHelper.getWeb3j().ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).send().getTransactionCount();
        } catch (Exception e) {
            log.error("Failed to get nonce :{}", e.getMessage());
        }
        return transactionCount;
    }

    public BigInteger ethBlockNumber() {
        BigInteger latestBlockNumber = null;

        try {
            latestBlockNumber = Web3jHelper.getWeb3j().ethBlockNumber().send().getBlockNumber();
        } catch (IOException e) {
            log.error("Failed to get the latest block number: {}", e.getMessage());
        }

        return latestBlockNumber;
    }

    public BigInteger ethGetBalance(String address) {
        BigInteger balance = null;

        try {
            balance = Web3jHelper.getWeb3j().ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
        } catch (IOException e) {
            log.error("Failed to query ETH balance: {}", e.getMessage());
        }
        return balance;
    }

    /**
     * Query TOKEN balances
     *
     * @param miner
     * @param tokenAddress
     * @return
     */
    public BigInteger balanceOfInContract(String tokenAddress, String miner) {
        BigInteger r = null;
        try {
            r = ContractBuilder.nestMiningContract(Web3jHelper.getWeb3j()).balanceOf(tokenAddress, miner).send();
        } catch (Exception e) {
            log.error("Failed to obtain the unfrozen ERC20 balance within the contract：{}", e.getMessage());
        }
        return r;
    }


    /**
     * Gets the INDEX of the last quotation in the specified quote list
     *
     * @return
     */
    public BigInteger lastIndex(BigInteger channelId) {
        List<PriceSheetView> list = list(channelId, BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO);
        if (CollectionUtils.isEmpty(list)) {
            return BigInteger.ZERO;
        }
        return list.get(0).index;
    }

    /**
     * Gets a frozen quotation at a specified address
     *
     * @param miner        Address of Quotation Miner
     * @param maxFindNum   The maximum number of quotations traversed is also the number of arrays returned
     * @return
     */
    public List<PriceSheetView> unClosedSheetListOf(String miner, BigInteger channelId, BigInteger maxFindNum) {
        List<PriceSheetView> list = list(channelId, BigInteger.ZERO, maxFindNum, BigInteger.ZERO);

        List<PriceSheetView> reulst = null;
        if (!CollectionUtils.isEmpty(list)) {
            reulst = new ArrayList<>();
            for (PriceSheetView priceSheetView : list) {
                if (!priceSheetView.miner.getValue().equalsIgnoreCase(miner)
                        || (priceSheetView.ethNumBal.compareTo(BigInteger.ZERO) <= 0
                        && priceSheetView.tokenNumBal.compareTo(BigInteger.ZERO) <= 0))
                    continue;
                reulst.add(priceSheetView);
            }
        }

        return reulst;
    }

    public List<PriceSheetView> list(BigInteger channelId, BigInteger offset, BigInteger count, BigInteger order) {
        List<PriceSheetView> sheetList = null;
        try {
            sheetList = ContractBuilder.nestMiningContract(Web3jHelper.getWeb3j()).list(channelId, offset, count, order).send();
        } catch (Exception e) {
            log.error("list 接口查询失败：{}", e);
        }
        return sheetList;
    }

    /**
     * Gets a collection of unthawed quotations at the specified address
     *
     * @param miner        Address of Quotation Miner
     * @param channelId
     * @param maxFindNum   The maximum number of quotations to traverse
     * @return
     */
    public List<Uint256> canClosedSheetIndexs(String miner, BigInteger channelId, BigInteger maxFindNum) {
        List<PriceSheetView> list = unClosedSheetListOf(miner, channelId, maxFindNum);
        if (CollectionUtils.isEmpty(list)) return null;

        BigInteger blockNumber = ethBlockNumber();
        if (blockNumber == null) {
            log.error("CanclosedSheetIndexs : failed to get the latest block number");
            return null;
        }

        int size = list.size();

        List<Uint256> closeIndexs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            PriceSheetView priceSheet = list.get(i);
            BigInteger height = priceSheet.getHeight();
            if (blockNumber.subtract(height).compareTo(nestProperties.getPriceDurationBlock()) > 0) {
                closeIndexs.add(new Uint256(priceSheet.getIndex()));
            }
        }

        return closeIndexs;
    }

    public BigInteger ethBalanceOfErc20(String address, String erc20TokenAddress) {
        BigInteger balance = null;

        try {
            balance = ContractBuilder.erc20Readonly(erc20TokenAddress, Web3jHelper.getWeb3j()).balanceOf(address).send();
        } catch (Exception e) {
            log.error("Query ERC20 balance failed: {}", e.getMessage());
        }
        return balance;
    }

    /**
     * Get the quotation in the validation period
     *
     * @return
     */
    public List<PriceSheetView> unVerifiedSheetList(BigInteger channelId) {
        List<PriceSheetView> list = list(channelId, BigInteger.ZERO, verifyState.getMaxFindNum(), BigInteger.ZERO);
        if (CollectionUtils.isEmpty(list)) return null;
        // The current latest block number
        BigInteger blockNumber = ethBlockNumber();
        if (blockNumber == null) {
            log.error("unVerifiedSheetList Failed to get the latest block number");
            return null;
        }

        int size = list.size();

        List<PriceSheetView> unVerifiedSheetList = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            PriceSheetView priceSheetView = list.get(i);
            BigInteger height = priceSheetView.getHeight();
            if (blockNumber.subtract(height).compareTo(nestProperties.getPriceDurationBlock()) < 0) {
                unVerifiedSheetList.add(priceSheetView);
            }
        }

        return unVerifiedSheetList;
    }

    /**
     * Initiate order trading
     *
     * @return
     */
    public String bite(String method, Wallet wallet, BigInteger gasPrice, BigInteger nonce, List typeList, BigInteger payableEth) {
        Function function = new Function(method, typeList, Collections.<TypeReference<?>>emptyList());
        String encode = FunctionEncoder.encode(function);
        String transaction = null;
        try {
            RawTransaction rawTransaction = RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    GasLimit.BITE_GAS_LIMIT,
                    nestProperties.getNestMiningAddress(),
                    payableEth,
                    encode);
            EthSendTransaction ethSendTransaction = ethSendRawTransaction(wallet.getCredentials(), rawTransaction);
            transaction = ethSendTransaction.getTransactionHash();
            if (ethSendTransaction.hasError()) {
                Response.Error error = ethSendTransaction.getError();
                log.error("Bite transaction return failed：[msg = {}],[data = {}],[code = {}],[result = {}],[RawResponse = {}],",
                        error.getMessage(),
                        error.getData(),
                        error.getCode(),
                        ethSendTransaction.getResult(),
                        ethSendTransaction.getRawResponse());
            }
        } catch (Exception e) {
            log.error("Send {} transaction failed: {}", method, e.getMessage());
        }
        return transaction;
    }

    /**
     * Bulk defrost quotation
     *  @param wallet
     * @param indices
     * @param channelId
     */
    public String close(Wallet wallet, BigInteger nonce, List<Uint256> indices, BigInteger channelId) {
        if (CollectionUtils.isEmpty(indices)) return null;
        Credentials credentials = wallet.getCredentials();
        if (nonce == null) {
            log.error("{} ：close, Failed to get nonce", credentials.getAddress());
            return null;
        }

        BigInteger gasPrice = ethGasPrice(gasPriceState.closeSheet);

        if (gasPrice == null) {
            log.error("close: Failed to get GasPrice");
            return null;
        }

        List<Type> typeList = Arrays.<Type>asList(
                new Uint256(channelId),
                new DynamicArray(Uint256.class, indices));
        Function function = new Function("close", typeList, Collections.<TypeReference<?>>emptyList());
        String encode = FunctionEncoder.encode(function);

        BigInteger payableEth = BigInteger.ZERO;

        String transactionHash = null;
        try {

            // Batch defrosting due to the size is larger than the fixed, here estimated gas
            org.web3j.protocol.core.methods.request.Transaction transaction =
                    org.web3j.protocol.core.methods.request.Transaction.
                            createFunctionCallTransaction(
                                    wallet.getCredentials().getAddress(),
                                    nonce,
                                    gasPrice,
                                    null,
                                    nestProperties.getNestMiningAddress(),
                                    encode);
            BigInteger amountUsed = getTransactionGasLimit(transaction);
            if (amountUsed == null) {
                amountUsed = GasLimit.CLOSE_GAS_LIMIT;
            } else {
                amountUsed = amountUsed.add(Constant.BIG_INTEGER_200K);
            }

            RawTransaction rawTransaction = RawTransaction.createTransaction(
                    nonce,
                    gasPrice,
                    amountUsed,
                    nestProperties.getNestMiningAddress(),
                    payableEth,
                    encode);
            EthSendTransaction ethSendTransaction = ethSendRawTransaction(credentials, rawTransaction);
            transactionHash = ethSendTransaction.getTransactionHash();
            if (ethSendTransaction.hasError()) {
                Response.Error error = ethSendTransaction.getError();
                log.error("Close Transaction return failed:[msg = {}],[data = {}],[code = {}],[result = {}],[RawResponse = {}],",
                        error.getMessage(),
                        error.getData(),
                        error.getCode(),
                        ethSendTransaction.getResult(),
                        ethSendTransaction.getRawResponse());
            }
        } catch (Exception e) {
            log.error("Failed to send Close transaction: {}", e.getMessage());
        }
        return transactionHash;
    }

    public EthSendTransaction ethSendRawTransaction(Credentials credentials, RawTransaction rawTransaction) throws ExecutionException, InterruptedException {

        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction,nestProperties.getChainId(),  credentials);
        String hexValue = Numeric.toHexString(signedMessage);

        Web3j web3j = Web3jHelper.getWeb3j();
        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).sendAsync().get();
        return ethSendTransaction;
    }

    /**
     * Forecast gasLimit
     *
     * @param transaction
     * @return
     */
    public BigInteger getTransactionGasLimit(org.web3j.protocol.core.methods.request.Transaction transaction) {
        BigInteger amountUsed = null;
        try {
            EthEstimateGas ethEstimateGas = Web3jHelper.getWeb3j().ethEstimateGas(transaction).send();
            if (ethEstimateGas.hasError()) {
                log.error("Estimate GasLimit exceptions: {}", ethEstimateGas.getError().getMessage());
                return amountUsed;
            }
            amountUsed = ethEstimateGas.getAmountUsed();
        } catch (IOException e) {
            log.error("Estimate GasLimit exceptions: {}", e.getMessage());
        }
        return amountUsed;
    }

}
