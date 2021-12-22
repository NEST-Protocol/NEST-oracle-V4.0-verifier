package com.nest.ib.controller;

import com.nest.ib.model.R;
import com.nest.ib.state.GasPriceState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;


@RestController
@RequestMapping("/gasPrice")
public class GasPriceController {

    @Autowired
    private GasPriceState gasPriceState;

    @GetMapping("")
    public ModelAndView miningData() {
        ModelAndView mav = new ModelAndView("gasPrice");

        mav.addObject("gasPriceState", gasPriceState);
        return mav;
    }

    @PostMapping("/updateGasPrice")
    public R updateGasPrice(@RequestParam(name = "baseBiteTypeGasPriceMul") BigDecimal baseBiteTypeGasPriceMul,
                      @RequestParam(name = "withdrawTypeGasPriceMul") BigDecimal withdrawTypeGasPriceMul,
                      @RequestParam(name = "closeSheetGasPriceMul") BigDecimal closeSheetGasPriceMul) {

        GasPriceState.Type baseBiteType = gasPriceState.getBaseBiteType();
        baseBiteType.setGasPriceMul(baseBiteTypeGasPriceMul);
        //
        GasPriceState.Type withdrawType = gasPriceState.getWithdrawType();
        withdrawType.setGasPriceMul(withdrawTypeGasPriceMul);
        //
        GasPriceState.Type closeSheet = gasPriceState.getCloseSheet();
        closeSheet.setGasPriceMul(closeSheetGasPriceMul);

        return R.ok();
    }
}
