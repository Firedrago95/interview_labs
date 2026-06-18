package com.example.demo.lotto;

import java.util.Arrays;

public class LottoApplication {
    public static void main(String[] args) {
        // 1. 발급기(LottoMachine) 준비: 우리가 만든 RandomNumberGenerator를 주입 (의존성 역전)
        NumberGenerator randomGenerator = new RandomNumberGenerator();
        LottoMachine machine = new LottoMachine(randomGenerator);

        int purchaseMoney = 14000;
        System.out.println("💰 " + purchaseMoney + "원을 투입하여 로또를 구매합니다.\n");

        // 2. 로또 구매 (LottoTicket이라는 일급 컬렉션 반환)
        LottoTicket ticket = machine.buy(purchaseMoney);

        // 3. 구매한 로또 번호 출력
        System.out.println("--- 구매한 로또 번호 ---");
        for (Lotto lotto : ticket.getLottos()) {
            System.out.println(lotto.getNumbers());
        }

        // 4. 지난주 당첨 번호 세팅 (예: 1, 2, 3, 4, 5, 6 / 보너스 7)
        System.out.println("\n🎯 당첨 번호를 세팅합니다: [1, 2, 3, 4, 5, 6] + 보너스 7");
        Lotto winningNumbers = new Lotto(Arrays.asList(1, 2, 3, 4, 5, 6));
        WinningLotto winningLotto = new WinningLotto(winningNumbers, 7);

        // 5. 로또 티켓에게 직접 대조해달라고 요청 (도메인에 로직 위임)
        LottoResult result = ticket.matchAll(winningLotto);

        // 6. 당첨 결과 통계 출력
        System.out.println("\n--- 당첨 통계 ---");
        for (Rank rank : Rank.values()) {
            if (rank == Rank.MISS) continue; // 꽝은 출력 생략
            
            System.out.printf("%d개 일치%s (%,d원) - %d개\n",
                    rank.getCountOfMatch(),
                    rank.isMatchBonus() ? ", 보너스 볼 일치" : "",
                    rank.getWinningMoney(),
                    result.getCountOf(rank));
        }

        // 7. 수익률 계산 및 출력
        double profitRate = result.calculateProfitRate(purchaseMoney);
        System.out.printf("총 수익률은 %.1f%%입니다.\n", profitRate);
    }
}
