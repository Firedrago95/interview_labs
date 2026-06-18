package com.example.demo.lotto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public class RandomNumberGenerator implements NumberGenerator{

    private static final List<Integer> ALL_NUMBERS = IntStream.rangeClosed(1, 45).boxed().toList();

    @Override
    public List<Integer> generate() {
        List<Integer> allNumbers = new ArrayList<>(ALL_NUMBERS);
        Collections.shuffle(allNumbers);

        List<Integer> selectedNumbers = allNumbers.subList(0, 6);

        // subList로 빼올때 나머지 값도 메모리에 계속 남기는 메모리 누수 방지
        List<Integer> result = new ArrayList<>(selectedNumbers);
        Collections.sort(result);
        return result;
    }
}
