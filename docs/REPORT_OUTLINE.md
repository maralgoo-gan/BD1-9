# Report.pdf — багшийн удирдамжийн 10 бүлэгт тааруулсан загвар

> Энд хоосон газар бодит үр дүнгээ нөхнө. Тест хийгээгүй байж PASS гэж бүү бич.

**Нүүр:** Сургуулийн нэр, F.CSM302, төсөл: №9 Designing a Thread Pool, хуваарилалтын код, багийн гурван гишүүн, багш, он.

**Хураангуй:** Номын thread pool механизм, Java synchronization, Worker Activity UI, үндсэн туршилт, батлагдсан үр дүн.

## 1. Удиртгал
Олон task-ийг тогтмол тооны worker гүйцэтгэх хэрэгцээ; бүтээсэн программын зорилго, хүрээ.

## 2. Сурах бичгийн шаардлага
Operating System Concepts (2018), Chapter 7, Programming Project 1, Java variant. `ThreadPool()`, `ThreadPool(int)`, `add(Runnable)`, `shutdown()` API. Ажлын queue, idle worker waiting, notification, interrupt. Багшийн нэмэлт шаардлага: параметрүүд, interactive worker activity timeline.

| Шаардлага | Кодын хэрэгжилт | Нотолгоо |
|---|---|---|
| Constructor | src/ThreadPool.java | PoolTests constructor |
| add(Runnable) | synchronized FIFO queue | Queue tests |
| Idle worker wait | wait()/notifyAll() | Empty-queue / Add Batch demo |
| Shutdown | interrupt() + queue cancellation | Shutdown test |
| Worker activity | Swing timeline / immutable snapshot | Бодит screenshot |

## 3. Онолын үндэс
Thread, Thread Pool, concurrency, shared FIFO queue, critical section, mutex/monitor, wait/notifyAll, race condition, deadlock болон interruption. "Зэрэг ажиллах" ба "яг зэрэг CPU core ашиглах"-ыг ялгаж тайлбарла.

## 4. Системийн зохиомж
UI → ThreadPool API → queue → worker threads → task execution → immutable snapshot → UI. Энэ диаграмыг зураг болгож тайланд оруул.

## 5. Хэрэгжилт
ThreadPool/Worker loop, WorkTask, snapshot, listener, Swing UI; гол synchronization кодын богино тайлбар. Queue-ийн lock-ийг task ажиллуулах хугацаанд барьдаггүйг тайлбарлах.

## 6. Туршилт ба UI дүрслэл
Workers, capacity, batch, duration, mode, start, add, shutdown, reset; worker IDLE/RUNNING, task COMPLETED/FAILED/CANCELLED, timeline, task table, event log.

## 7. Туршилтын арга
Хэрэглэсэн бодит төхөөрөмж, macOS, JDK болон compiler хувилбар, тест хийх командууд, оролт, хүлээгдсэн үр дүнг хүснэгтээр оруул.

## 8. Үр дүн
Тестийн бодит үр дүн, screenshot. Нэг worker ба гурван worker-ийн бодит хугацааг хэмжиж харьцуулах. Номын API болон GUI шаардлагуудыг нотлох.

## 9. Хэлэлцүүлэг
Зэрэгцээ ажиллагаа, queue waiting, FIFO submit/dequeue vs completion order, race-free state updates, shutdown mode. Хязгаарлалт: энэ нь OS kernel scheduler биш; Sleep mode I/O wait simulation.

## 10. Дүгнэлт
Ямар шаардлага бүрэн биелсэн, юу туршиж нотолсон, ямар сайжруулалт ирээдүйд хийхийг дүгнэх.

**Ашигласан материал:** Ном, багшийн OS_BD1_Guide, Java API documentation, ашигласан программ/сан, AI туслалцааны disclosure.

**Хавсралт А:** Хуваарилалтын код, Repository, Орчин, UI, гишүүдийн ажил/commit, үндсэн 5 тест.
