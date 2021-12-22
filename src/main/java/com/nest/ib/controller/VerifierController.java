package com.nest.ib.controller;

import com.nest.ib.model.R;
import com.nest.ib.state.VerifyState;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;

@RestController
@RequestMapping("/verifier")
public class VerifierController {
    @Autowired
    private VerifyState verifyState;

    @GetMapping("")
    public ModelAndView miningData() {
        ModelAndView mav = new ModelAndView("verifier");
        mav.addObject("src", "/verifier");
        mav.addObject("verifyState", verifyState);

        return mav;
    }

    /**
     * Enable/close the verifier. True on,false off
     */
    @PostMapping("/updateBiteState")
    public R updateBiteState() {
        if (verifyState.isOpen()) {
            verifyState.close();
        } else {
            verifyState.open();
        }
        return R.ok();
    }

    /**
     * Whether to enable hedging. True on,false off
     */
    @PostMapping("/updateHedgeState")
    public R updateHedgeState() {
        verifyState.setHedge(!verifyState.isHedge());
        return R.ok();
    }


    /**
     * Token  threshold configuration
     */
    @PostMapping("/updateTokenBiteInfo")
    public R updateTokenBiteInfo(@RequestParam(name = "biteRate") BigDecimal biteRate) {
        verifyState.setTokenBiteThreshold(biteRate);
        return R.ok();
    }

    @PostMapping("/updateBiteOtherSetting")
    public R updateBiteOtherSetting(@RequestParam(name = "closeMinNum") int closeMinNum) {
        verifyState.setCloseMinNum(closeMinNum);
        return R.ok();
    }
}
