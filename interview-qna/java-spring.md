# ☕️ Java & Spring 면접 기출 및 핵심 이론

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
<summary><b>Q. JVM의 메모리 구조에 대해 설명해주세요.</b></summary>
<div markdown="1">

- **핵심 키워드**: Method Area, Heap, Stack, PC Register, Native Method Stack
- **나의 답변**: JVM 메모리는 크게 모든 스레드가 공유하는 영역인 Heap과 Method Area, 그리고 스레드마다 개별적으로 생성되는 Stack, PC Register, Native Method Stack으로 나뉩니다. (상세 내용 작성...)
- **💡 꼬리질문**:
  - *Q. 객체를 생성하면 메모리의 어느 영역에 할당되나요?*
    - A. 런타임에 동적으로 할당되는 데이터이므로 Heap 영역에 저장되며, 가비지 컬렉터(GC)의 관리 대상이 됩니다.
</div>
</details>
