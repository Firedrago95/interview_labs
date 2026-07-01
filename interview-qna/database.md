# 🗄️ Database 면접 기출 및 핵심 이론

> **📝 작성 가이드 (아래 템플릿을 복사해서 새 질문을 추가하세요)**
> ```markdown
> <details>
> <summary><b>Q. 질문 제목을 적어주세요.</b></summary>
> <div markdown="1">
> 
> - **핵심 키워드**: 대답에 반드시 포함되어야 할 키워드
> - **나의 답변**: 면접에서 실제로 말할 스크립트 형태의 답변
> - **💡 꼬리질문**:
>   - *Q. 꼬리질문 내용?*
>     - A. 꼬리질문 답변 내용
> </div>
> </details>
> ```

---

<details>
<summary><b>Q. 트랜잭션의 격리 수준(Isolation Level) 4가지와 각각 발생하는 문제점에 대해 설명해주세요.</b></summary>
<div markdown="1">

- **핵심 키워드**: Read Uncommitted, Read Committed, Repeatable Read, Serializable / Dirty Read, Non-Repeatable Read, Phantom Read
- **나의 답변**: 트랜잭션 격리 수준은 여러 트랜잭션이 동시에 처리될 때 특정 트랜잭션이 다른 트랜잭션에서 변경하거나 조회하는 데이터를 볼 수 있게 허용할지 여부를 결정하는 것입니다. (상세 내용 작성...)
- **💡 꼬리질문**:
  - *Q. MySQL(InnoDB)의 기본 격리 수준은 무엇이고, Phantom Read가 발생하나요?*
    - A. Repeatable Read입니다. 표준 스펙상 Phantom Read가 발생할 수 있지만, InnoDB는 넥스트 키 락(Next-Key Lock)을 사용하여 이를 방지합니다.
</div>
</details>
