# V-1-9 — Reviewer Comments and Responses (БД2)

**Төсөл:** Designing a Thread Pool — Урсгалын сан / ажиллагчдын төлөв  
**Нягталсан шүүмж:** `V-1-9-review.pdf` (II-4-13 баг, 18 хуудас), хэсэг 7–8; шалгасан commit `5218f6e` (2026-11-05).  
**БД2 удирдамж:** `OS_BD2_Guide.pdf`, хуудас 2–5.  
**Эх хувилбар:** хэрэглэгчийн оруулсан `BD1-9.zip` (README.md агуулж байсан; шүүмжлэгчдийн шалгасан хуучин commit-ээс ялгаатай).  
**Эцсийн Git commit/tag:** *баг GitHub-д push хийсний дараа бодит hash болон tag-ийг энд нөхнө*.  
**Нотолгооны орчин:** Debian GNU/Linux 13 x86_64, OpenJDK 21.0.12.1; GUI зураг Linux/Xvfb (1600×1000) дээр авсан. **Mac/Windows дээр энэ удаа шалгаагүй.**

| ID | Шүүмж (эх утгыг товчилсон) | Түвшин | Шийдвэр | Бодит өөрчлөлт, нотолгоо | Статус |
|---|---|---|---|---|---|
| P1 | README байхгүй; Release/Tag байхгүй; repo-д PDF дутуу | Major | Зөвшөөрсөн | ZIP-д README аль хэдийн байсан тул БД2 хэсэг, Quick Start, screenshot, GitHub заавар нэмж шинэчилсэн; `.gitignore` цэвэрлэсэн. **GitHub release/tag ба жинхэнэ commit-ийг баг publish хийх үлдсэн.** | Хэсэгчлэн гүйцэтгэсэн |
| P2 | Manual/Report-д хоосон `[бөглөнө]` хэсэг | Major | Зөвшөөрсөн | `FINAL_MANUAL.md`, `FINAL_REPORT_DRAFT.md`-д бодитоор хийсэн тест, Linux орчин, зураг, өмнөх/дараах хэмжилт оруулсан. Гишүүд, Mac тест, git commit-ийн мэдээлэл нөхөх шаардлагатай. | Хэсэгчлэн гүйцэтгэсэн |
| P3 | GitHub ажиллагаа, хуваарилалт, AI/бэлэн кодын тэмдэглэл дутуу | Major | Зөвшөөрсөн | README, Report-д GitHub ажиллагааны хэсэг, AI ашигласан тухай ил тод тэмдэглэл нэмсэн. **Багийн бодит commit/үүргийг зохиож бичээгүй**. | Хэсэгчлэн гүйцэтгэсэн |
| P4 | ~1000 ажлын дараа 4000 event хасагдсанаас timeline хуурамч IDLE болно | Major | Зөвшөөрсөн | `TimelinePanel`-ийг `TaskView.startMillis/finishMillis`-д тулгуурласан; `WorkerView.stoppedMillis` нэмсэн. Event log 4000 хүртэл боловч timeline хамааралгүй. Ижил 3001 ажлын P4 probe: **өмнө false → дараа true**. | Зассан, тестээр нотолсон |
| P5 | FAILED, CANCELLED, STOPPED нэг улаан өнгө ашигладаг; legend дутуу | Minor | Зөвшөөрсөн | FAILED шар, CANCELLED бараан саарал, STOPPED улаан, COMPLETED цэнхэр; тайлбар нэмсэн; x-тэнхлэгийн сүүлийн бичиг захад багтана. Бодит GUI screenshot хавсаргав. | Зассан |
| P6 | Manual-д буруу утгад error гарна гэсэн ч spinner хязгаартай; API/UI range өөр | Minor | Хэсэгчлэн зөвшөөрсөн | `commitEdit()` ба ParseException мэдэгдлийг UI-д нэмсэн; Manual-д **GUI 1–32/1–1000, API 1–64/1–10000** гэдгийг ил тод тайлбарлав. API-ийн өргөн хүрээг тестэнд хэрэглэх тул зориуд хадгалсан. | Тайлбар, validation сайжирсан |
| P7 | Shutdown-ийн яг зааг дээр task state COMPLETED/CANCELLED будилах тохиолдол | Minor | Хэсэгчлэн зөвшөөрсөн | `WorkTask` interrupt-д CancellationException шиддэг хэвээр; generic Runnable-ийн `interrupt` үл тоох/залгих боломжийг Known Issues-д тодорхой бичсэн. **Бүх төрлийн Runnable-д 100% шийдсэн гэж үзэхгүй.** | Хязгаарлалтаар үлдээсэн |
| P8 | Event бүрийн snapshot, listener lock дотор; UI хүснэгтийг бүтнээр дахин үүсгэдэг | Minor | Зөвшөөрсөн | UI listener callback-ийг хэрэглэхээ больж, 150 мс тутам 1 snapshot асуудаг болсон; listener-ийг lock-оос гадна, 100 мс rate-limit-тай дуудна; event `ArrayDeque`, хүснэгтийн зөвхөн өөрчлөгдсөн нүд шинэчилнэ. 400 мс listener тестийн үед `submit()` сааталгүй тэнцсэн. | Зассан, тестээр нотолсон |
| P9 | FIFO, concurrent producers, repeated shutdown, GUI smoke regression нэмэх | Suggestion | Зөвшөөрсөн | `BD2RegressionTests.java`: FIFO 30, 8 producer × 200, давтан lifecycle 30, 2000 snapshot invariant, 3001 P4, 400 мс listener. `GuiSmokeTest` Xvfb дээр ажилласан. | Зассан |
| P10 | Windows дэмжлэг, timing тохиргоо, тайлбар дутуу | Suggestion | Хэсэгчлэн зөвшөөрсөн | `run.bat`, `test.bat` нэмсэн, Windows командын заавартай. **Windows дээр бодитоор туршаагүй.** | Туршилт үлдсэн |
| P11 | Диаграм, workerLoop тайлбар, glossary, screenshot дээрх тайлбар дутуу | Suggestion | Зөвшөөрсөн | Final Report ба Manual-д текстэн архитектур/төлөвийн урсгал, workerLoop алхам, нэр томьёо, бодит зураг нэмсэн. | Баримтжуулсан |
| P12 | Анхлан суралцагчдад алхам бүрээр тайлбар хэрэгтэй | Suggestion | Зөвшөөрсөн | `FINAL_MANUAL.md`: 8 алхмын анхны туршилт, төлөвийн тайлбар, 3 сценарийн хүлээгдэх үр дүн нэмсэн. | Баримтжуулсан |

## Техникийн хязгаарлалтыг зөвшөөрөх үндэслэл (P6, P7)

**P6:** API нь `ThreadPool(64, 10000)`-ийг дэмждэг, харин 32 worker/1000 queue хүртэлх spinner нь нэг дэлгэц дээр хэрэглэгчид тохиромжтой хязгаар юм. Тестээр API-ийн илүү өргөн хүрээг хэрэглэх боломжтой. UI-д буруу текст шивэхэд parser-ийн алдааг тусад нь мэдэгдэнэ. Мэдэгдэл GUI-ийн *бүх* буруу numeric утгад гарахыг амлахгүй, spinner өөрөө зарим оролтыг хаана.

**P7:** Pool нь Runnable-ийн дотоод ажил бүрэн гүйцсэн эсэхийг 100% шалгаж чадахгүй: `run()` interrupt-ийг үл тоох, эсвэл `InterruptedException`-ийг залгиж болно. Энэ төслийн `WorkTask` нь interruption-ийг cooperation буюу CancellationException/interrupt flag-аар мэдэгдэнэ. Иймд generic Runnable-ийн boundary race хэвээр гэдгийг ил тод тэмдэглэж, зөв ажиллагааг хэтрүүлэн амлахгүй.

## GitHub-д нийтлэхийн өмнөх заавал хийх ажлууд

1. Багийн гурван гишүүн бодит нэр/үүрэг болон тэдний хийсэн commit-ийг зөвшилцөж баримтжуулах.
2. Багийн Mac дээр `bash test.sh`, `bash run.sh`, GUI хэрэглээг дахин шалгах.
3. `git add`, commit, push хийж **жинхэнэ hash/tag** гаргах, Report-ийн нүүр болон README-д ижил tag оруулах.
4. GitHub Release үүсгээд Poster-ийн QR-ийг **тухайн release URL** руу шинэчлэх.
5. Final Report-ийг DRAFT биш эцсийн нэрээр экспортлон Teams-д Poster, Manual, Report, Release холбоостой оруулах.
