package com.nest.ib.state;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * @author wll
 * @date 2020/12/30 11:10
 */
@Component
public class GasPriceState {

    /**
     * Verify to eat single GASPrice multiple
     */
    public Type baseBiteType = new Type(new BigDecimal("1.1"));

    /**
     * Take out the assets :1.1 times
     */
    public Type withdrawType = new Type(new BigDecimal("1.1"));

    /**
     * Defrost the asset GASPrice
     */
    public Type closeSheet = new Type(new BigDecimal("1.1"));

    /**
     * Authorized transactions: 1.2 times
     */
    public Type approveType = new Type(new BigDecimal("1.2"));


    public static class Type {

        private volatile BigDecimal gasPriceMul = BigDecimal.ONE;

        public Type(BigDecimal gasPriceMul) {
            this.gasPriceMul = gasPriceMul;
        }

        public BigDecimal getGasPriceMul() {
            return gasPriceMul;
        }

        public void setGasPriceMul(BigDecimal gasPriceMul) {
            this.gasPriceMul = gasPriceMul;
        }
    }

    public Type getCloseSheet() {
        return closeSheet;
    }

    public void setCloseSheet(Type closeSheet) {
        this.closeSheet = closeSheet;
    }

    public Type getBaseBiteType() {
        return baseBiteType;
    }

    public void setBaseBiteType(Type baseBiteType) {
        this.baseBiteType = baseBiteType;
    }

    public Type getApproveType() {
        return approveType;
    }

    public void setApproveType(Type approveType) {
        this.approveType = approveType;
    }

    public Type getWithdrawType() {
        return withdrawType;
    }

    public void setWithdrawType(Type withdrawType) {
        this.withdrawType = withdrawType;
    }
}
