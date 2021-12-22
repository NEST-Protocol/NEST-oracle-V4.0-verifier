package com.nest.ib.state;

import com.nest.ib.config.NestProperties;
import com.nest.ib.constant.Constant;
import com.nest.ib.contract.ContractBuilder;
import com.nest.ib.contract.ERC20;
import com.nest.ib.contract.PriceChannelView;
import com.nest.ib.helper.Web3jHelper;
import com.nest.ib.service.PriceService;
import com.nest.ib.utils.EthClient;
import com.nest.ib.utils.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.web3j.abi.datatypes.Address;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @author wll
 * @date 2020/12/28 11:39
 */
@Component
public class Erc20State {

    private static final Logger log = LoggerFactory.getLogger(Erc20State.class);

    private static final int MAX_SIZE = 50;

    @Autowired(required = false)
    private PriceService priceService;
    @Autowired
    private NestProperties nestProperties;

    public volatile BigInteger channelId;

    /**
     * Token information
     */
    public volatile Item token = new Item();

    public volatile Item token1 = new Item();

    public volatile BigDecimal unit;

    public volatile BigDecimal token0Token1Pirce = null;


    public BigInteger getNeedToken0() {
        return unit.multiply(token.getDecPowTen()).toBigInteger();
    }

    public BigInteger getNeedToken1() {
        return token0Token1Pirce.multiply(unit).multiply(token1.getDecPowTen()).toBigInteger();
    }

    public static class Item {

        private volatile String address;

        private volatile Boolean zero;

        private volatile String symbol = "";

        private volatile Integer decimals;

        /**
         * 10 to the power of decimals
         */
        private volatile BigDecimal decPowTen;

        /**
         * The index of the quotation that has been validated
         */
        private LinkedBlockingDeque<BigInteger> biteIndex = new LinkedBlockingDeque<>();

        public void addBiteIndex(BigInteger index) {
            log.info("{} validation index: {}", symbol, index);
            if (biteIndex.size() > MAX_SIZE) {
                biteIndex.poll();
            }
            biteIndex.offer(index);
        }

        public boolean haveEaten(BigInteger index) {
            return biteIndex.contains(index);
        }

        public Integer getDecimals() {
            return decimals;
        }

        public void setDecimals(Integer decimals) {
            this.decimals = decimals;
            log.info("****{} decimals update：{}****", symbol, decimals);
        }

        public BigDecimal getDecPowTen() {
            return decPowTen;
        }

        public void setDecPowTen(BigDecimal decPowTen) {
            this.decPowTen = decPowTen;
            log.info("****{} decPowTen update：{}****", symbol, decPowTen);
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
            log.info("****{} address update：{}****", symbol, address);
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
            log.info("**** symbol update：{}****", symbol);
        }

        public Boolean getZero() {
            return zero;
        }

        public void setZero(Boolean zero) {
            this.zero = zero;
        }
    }


    public boolean updateErc20State(BigInteger channelId) throws Exception {

        try {
            // Gets and configures token information
            this.token0Token1Pirce = null;
            this.channelId = null;
            PriceChannelView priceChannelView = ContractBuilder.nestMiningContract(Web3jHelper.getWeb3j()).getChannelInfo(channelId).send();
            updateErc20BaseState(token, priceChannelView.token0);
            updateErc20BaseState(token1, priceChannelView.token1);
            nestProperties.setMiningFee(MathUtils.intDivDec(priceChannelView.postFeeUnit, nestProperties.getMiningFeeRateInflateFactor(), 5));

            this.unit = MathUtils.intDivDec(priceChannelView.unit, token.getDecPowTen(), 4);
        } catch (Exception e) {
            log.error("Failed to obtain token information. Unable to quote. Please check whether the token address is correct or the node is normal:{}", e.getMessage());
            throw new Exception("Failed to obtain token information. Unable to quote. Please check whether the token address is correct or the node is normal");
        }

        this.channelId = channelId;

        return true;
    }

    private void updateErc20BaseState(Item item, String tokenAddress) throws Exception {
        if (tokenAddress.equalsIgnoreCase(Address.DEFAULT.getValue())) {
            item.setSymbol("BNB");
            item.setDecimals(18);
            item.setDecPowTen(Constant.UNIT_DEC18);
        } else {

            ERC20 erc20 = ContractBuilder.erc20Readonly(tokenAddress, Web3jHelper.getWeb3j());
            String symbol = erc20.symbol().send();
            item.setSymbol(symbol);

            int decimals = erc20.decimals().send().intValue();
            item.setDecimals(decimals);
            long decPowTen = (long) Math.pow(10, decimals);
            item.setDecPowTen(new BigDecimal(decPowTen));
        }

        item.setAddress(tokenAddress);
        item.setZero(tokenAddress.equalsIgnoreCase(Address.DEFAULT.getValue()));

    }

    public boolean updateToken0Token1Price() {

        Assert.isTrue(priceService != null, "Priceservice interface not implemented");

        token0Token1Pirce = priceService.getToken0Token1Price();

        if (token0Token1Pirce == null) {
            log.error("Price acquisition failed. Trading pair price set to NULL. Quote trading has been suspended");
            return false;
        }

        log.info("Update {}{} price: {}", token.symbol, token1.symbol, token0Token1Pirce);
        return true;
    }
}
