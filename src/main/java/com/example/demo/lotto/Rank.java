package com.example.demo.lotto;

import java.util.Arrays;

public enum Rank {

    FIRST(6, false, 2_000_000_000),
    SECOND(5, true, 30_000_000),
    THIRD(5, false, 1_500_000),
    FOURTH(4, false, 50_000),
    FIFTH(3, false, 5_000),
    MISS(0, false, 0);

    private final int countOfMatch;
    private final boolean matchBonus;
    private final int winningMoney;

    Rank(int countOfMatch, boolean matchBonus, int winningMoney) {
        this.countOfMatch = countOfMatch;
        this.matchBonus = matchBonus;
        this.winningMoney = winningMoney;
    }

    public static Rank valueOf(int countOfMatch, boolean matchBonus) {
        if (countOfMatch == 5) {
            return matchBonus ? SECOND : THIRD;
        }
        return Arrays.stream(values())
            .filter(rank -> rank.countOfMatch == countOfMatch)
            .findFirst()
            .orElse(MISS);
    }

    public int getWinningMoney() {
        return winningMoney;
    }
}
