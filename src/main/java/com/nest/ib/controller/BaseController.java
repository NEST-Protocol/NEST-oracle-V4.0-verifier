package com.nest.ib.controller;

import com.nest.ib.config.NestProperties;
import com.nest.ib.helper.Web3jHelper;
import com.nest.ib.model.R;
import com.nest.ib.model.Wallet;
import com.nest.ib.helper.WalletHelper;
import com.nest.ib.state.Erc20State;
import com.nest.ib.state.GasPriceState;
import com.nest.ib.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.web3j.crypto.Credentials;

import java.math.BigInteger;

/**
 * Basic configuration
 */
@RestController
@RequestMapping("/base")
public class BaseController {

    private static final Logger log = LoggerFactory.getLogger(BaseController.class);

    @Autowired
    private Erc20State erc20State;

    @Autowired
    private GasPriceState gasPriceState;

    @Autowired
    private EthClient ethClient;

    @Autowired
    private WalletHelper walletHelper;

    @Autowired
    private NestProperties nestProperties;

    /**
     * Basic configuration page
     */
    @GetMapping("")
    public ModelAndView miningData() {
        ModelAndView mav = new ModelAndView("base");
        mav.addObject("node", Web3jHelper.getNode());
        mav.addObject("channelId",erc20State.channelId);

        mav.addObject("token", erc20State.token);
        mav.addObject("token1", erc20State.token1);

        mav.addObject("gasPriceState", gasPriceState);

        if (WalletHelper.getWallet() != null) {
            walletHelper.updateWalletBalance();
            mav.addObject("wallet", WalletHelper.getWallet());
        }
        return mav;
    }

    /**
     * Add a node
     */
    @PostMapping("/addNode")
    public R addNode(@RequestParam(name = "node") String node) {
        if (Web3jHelper.updateWeb3j(node)) {
            return R.ok();
        } else {
            return R.error("Node update failed. Please check if the node is available");
        }
    }


    /**
     * Add token quote address, exchange type, and trade pair
     *
     * @return
     */
    @PostMapping("/addToken")
    public R addToken(@RequestParam(name = "channelId") BigInteger channelId) {
        R r = null;
        try {
            boolean b = erc20State.updateErc20State(channelId);
            if (b) {

                // Update contract parameters
                boolean updateContractParams = nestProperties.updateContractParams(Web3jHelper.getWeb3j());
                if (updateContractParams) {
                    log.info("Contract parameters updated successfully");
                } else {
                    log.error("Contract parameter update failed. Quotation cannot be conducted");
                    return R.error("Contract parameter update failed. Quotation cannot be conducted");
                }
                return R.ok();
            } else {
                return R.error();
            }
        } catch (Exception e) {
            r = R.error(e.getMessage());
        }

        return r;
    }

    /**
     * Set the account private key
     */
    @PostMapping("/addWallet")
    public R addWallet(@RequestParam(name = "privateKey") String privateKey) {
        Wallet wallet = null;
        Credentials credentials = null;
        try {
            wallet = new Wallet();
            credentials = Credentials.create(privateKey);
            log.info(credentials.getAddress());
        } catch (Exception e) {
            log.error("Wallet creation failed：{}", e.getMessage());
            return R.error("Wallet creation failed:" + e.getMessage());
        }
        // Check the authorization
        wallet.setCredentials(credentials);
        try {
            ethClient.approveToNestMinningContract(wallet);
        } catch (Exception e) {
            log.error("Wallet authorization failed：{}", e.getMessage());
            return R.error("Wallet authorization failed:" + e.getMessage());
        }

        WalletHelper.updateWallet(wallet);
        log.info("Wallet updated successfully");
        return R.ok();
    }
}
