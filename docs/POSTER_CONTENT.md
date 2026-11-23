# Нэг нүүр Постер — баталгаатай баримт

- Төслийн нэр: V-1-9 — Thread Pool / Ажиллагчдын төлөв.
- Асуудал: Олон task-ийг хязгаарлагдмал worker-ээр, FIFO queue/interrupt ашиглан ажиллуулах.
- Шийдэл: UI → ThreadPool FIFO queue → worker Threads → TaskView snapshots → 150мс Swing timeline.
- Бодит screenshot: `docs/screenshots/worker-activity-completed.png` (Linux/Xvfb, 8 completed).
- Гол сайжруулалт: P4 (4000 event-ээс хамаарахгүй), P8 (UI polling/incremental), P5 (өнгө), P9 (нэмэлт тест).
- Before/after: 3001 task-ийн анхны 100 мс ажилласан түүх BD1 false → BD2 true.
- Regression: 7 үндсэн + timeline + 5 нэмэлт, GUI smoke PASS (Linux).
- Хязгаарлалт: Mac/Windows test, release tag, contributor credits багийн талд үлдсэн; P7 generic cancellation.
- QR: **repo** `https://github.com/maralgoo-gan/BD1-9` руу чиглүүлсэн *түр хувилбар*. GitHub final release нийтлэгдсэний дараа QR-ийг release link-ээр солино.
- Poster screenshot ба test outputs бодит байдлаар авсан; зохиогоогүй.
