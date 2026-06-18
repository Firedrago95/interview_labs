package com.example.demo.lotto;

import java.util.Map;

public class LottoResult {

    private final Map<Rank, Integer> result;

    public LottoResult(Map<Rank, Integer> result) {
        this.result = result;
    }

    public double calculateProfitRate(int purchaseMoney) {
        long totalPrize = 0;

        for (Map.Entry<Rank, Integer> entry : result.entrySet()) {
            totalPrize += (long) entry.getKey().getWinningMoney();
        }

        return (double) totalPrize / purchaseMoney * 100;
    }
}
