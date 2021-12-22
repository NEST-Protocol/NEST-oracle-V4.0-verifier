package com.nest.ib.helper;

import com.nest.ib.contract.PriceSheetView;
import com.nest.ib.service.BiteService;
import com.nest.ib.utils.EthClient;
import com.nest.ib.config.NestProperties;
import com.nest.ib.constant.Constant;
import com.nest.ib.model.Wallet;
import com.nest.ib.state.Erc20State;
import com.nest.ib.state.VerifyState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.web3j.tuples.generated.Tuple2;

import java.math.BigInteger;
import java.util.*;

/**
 * @author wll
 * @date 2020/8/24 9:47
 */
@Component
public class WalletHelper {

    private static final Logger log = LoggerFactory.getLogger(WalletHelper.class);
    private static Wallet WALLET;

    @Autowired
    private EthClient ethClient;
    @Autowired
    private NestProperties nestProperties;
    @Autowired
    private Erc20State erc20State;
    @Autowired
    private VerifyState verifyState;
    @Autowired
    private BiteService biteService;

    public static void updateWallet(Wallet wallet) {
        WALLET = wallet;
    }

    public static Wallet getWallet() {
        if (WALLET == null) {
            log.warn("The wallet is empty");
            return null;
        }

        return WALLET;
    }

    /**
     * Update wallet assets
     */
    public void updateWalletBalance() {
        if (WALLET != null) {
            if (!erc20State.updateToken0Token1Price()) return;
            updateBalance(WALLET, true);
        }
    }

    /**
     * Update each asset balance：
     *
     * @param sumTotal True counts all assets (account + unfrozen + frozen) and false does not count all assets
     */
    public boolean updateBalance(Wallet wallet, boolean sumTotal) {
        String address = wallet.getCredentials().getAddress();
        // Get the ETH balance of the account
        BigInteger ethBalance = ethClient.ethGetBalance(address);
        if (ethBalance == null) {
            return false;
        }
        // Get the account token balance
        BigInteger token0Balance = ethClient.balanceOfItem(erc20State.token, address);
        if (token0Balance == null) {
            return false;
        }

        BigInteger token1Balance = ethClient.balanceOfItem(erc20State.token1, address);
        if (token1Balance == null) {
            return false;
        }

        // Get the NEST balance of your account
        BigInteger nestBalance = ethClient.ethBalanceOfErc20(address, nestProperties.getNestTokenAddress());
        if (nestBalance == null) {
            return false;
        }

        log.info("{} account balance：ETH={}，{}={}，{}={}，nest={}", address, ethBalance, erc20State.token.getSymbol(), token0Balance, erc20State.token1.getSymbol(), token1Balance, nestBalance);
        Wallet.Asset account = wallet.getAccount();
        account.setNestAmount(nestBalance);
        account.setTokenAmount(token0Balance);
        account.setToken1Amount(token1Balance);
        account.setEthAmount(ethBalance);

        // Get the token balances of CLOSE
        BigInteger closeToken0 = ethClient.itemBalanceOfInContract(erc20State.token, address);
        BigInteger closeToken1 = ethClient.itemBalanceOfInContract(erc20State.token1, address);

        if (closeToken0 == null || closeToken1 == null) return false;

        Wallet.Asset closed = wallet.getClosed();
        closed.setEthAmount(BigInteger.ZERO);
        closed.setTokenAmount(closeToken0);
        closed.setToken1Amount(closeToken1);

        // Get the NEST balance of close, which contains the number of NEST mined, and can also be used for direct quotation
        BigInteger closeNest = ethClient.balanceOfInContract(nestProperties.getNestTokenAddress(), address);
        if (closeNest == null) return false;
        closed.setNestAmount(closeNest);

        log.info("{} Assets are unfrozen under the contract ，{}={}，{}={}，nest={}", address, erc20State.token.getSymbol(), closeToken0, erc20State.token1.getSymbol(), closeToken1, closeNest);

        // Current Total Available Balance, Account Balance + Unfrozen Balance
        Wallet.Asset useable = wallet.getUseable();
        Wallet.Asset useableTemp = new Wallet.Asset();
        useableTemp.addAsset(closed);
        useableTemp.addAsset(account);
        useable.setAsset(useableTemp);

        // Calculate total assets
        if (sumTotal) {
            Wallet.Asset freezedAssetTemp = new Wallet.Asset();

            // Token eating verifies frozen assets
            List<PriceSheetView> tokenPriceSheets = ethClient.unClosedSheetListOf(address, erc20State.channelId, verifyState.getMaxFindNum());
            addFreezedAsset(freezedAssetTemp, tokenPriceSheets, erc20State.token);

            wallet.getFreezed().setAsset(freezedAssetTemp);

            Wallet.Asset total = wallet.getTotal();
            Wallet.Asset totalTemp = new Wallet.Asset();
            totalTemp.addAsset(account);
            totalTemp.addAsset(wallet.getFreezed());
            totalTemp.addAsset(closed);
            total.setAsset(totalTemp);
        }
        return true;
    }

    private void addFreezedAsset(Wallet.Asset freezed, List<PriceSheetView> priceSheets, Erc20State.Item erc20) {
        if (!CollectionUtils.isEmpty(priceSheets)) {
            for (PriceSheetView priceSheet : priceSheets) {
                BigInteger nestNum1k = priceSheet.getNestNum1k();
                BigInteger nestNumBal = nestNum1k.multiply(Constant.BIG_INTEGER_1K);
                // Mortgage of the NEST
                BigInteger nestAmount = nestNumBal.multiply(Constant.UNIT_INT18);
                freezed.addNestAmount(nestAmount);

                // Ethnumbal is the amount of token0 left
                freezed.addTokenAmount(priceSheet.getEthNumBal().multiply(erc20State.getNeedToken0()));

                // TokenNumbal * TokenAmountPereth is the number of token1 left
                BigInteger freezedToken1 = priceSheet.getTokenNumBal().multiply(priceSheet.price);
                freezed.addToken1Amount(freezedToken1);
            }
        }
    }
}
