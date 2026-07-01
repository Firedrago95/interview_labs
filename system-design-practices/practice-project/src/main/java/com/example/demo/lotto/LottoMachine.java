package com.example.demo.lotto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class LottoMachine {

    private static final int LOTTO_PRICE = 1_000;

    private final NumberGenerator numberGenerator;

    public LottoMachine(NumberGenerator numberGenerator) {
        this.numberGenerator = numberGenerator;
    }

    public LottoTicket buy(int money) {
        if (money < LOTTO_PRICE) {
            throw new IllegalArgumentException("금액이 부족합니다.");
        }
        int count = money / LOTTO_PRICE;
        List<Lotto> lottos = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            lottos.add(new Lotto(numberGenerator.generate()));
        }
        return new LottoTicket(lottos);
    }
}
