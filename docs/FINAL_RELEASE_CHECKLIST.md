# БД2 — GitHub Final Release болон Teams-д нийтлэх checklist

**Энэ ZIP-г бэлдэх үед GitHub рүү өөрчлөлт push хийгээгүй.** Дараах алхмуудыг баг өөрсдийн Mac болон GitHub account дээр ажиллуулна.

## 1. Mac дээр дахин турших

```bash
cd ~/Desktop/BD1-9
java -version
javac -version
bash test.sh
bash run.sh
```

`run.sh` цонх нээгдвэл: 3 worker, 8 task, 2000 мс, Хүлээлт → 8/8 `ДУУССАН`; дараа нь `Шинэчлэх`, `Зогсоох` турш. Бодит Mac screenshot авбал Linux/Xvfb screenshot-ийн хамт эсвэл оронд нь Report-д нэм.

## 2. Гишүүд ба баримтыг эцэслэх

- `docs/FINAL_REPORT_DRAFT.md` болон PDF-ийн нүүрэнд бодит гишүүд, тэдний GitHub commit/PR-г оруулах.
- Бодит Mac-ийн ОС, CPU, RAM, JDK version, тестийн гаралтыг нэмж баталгаажуулах.
- AI туслалцаа, ашигласан кодын тэмдэглэлийг бодитоор хадгалах.
- P7 зэрэг бүрэн шийдээгүй Known Issues-ийг үлдээх.

## 3. GitHub push + final tag/release

Хэрэв zip-аас анх удаа авч байгаа бол GitHub-ын repository-г `git clone` хийж, зассан файлуудыг клонд хуулж оруул (ZIP-д `.git` байхгүй). Repository дотор:

```bash
git status
git add .
git commit -m "BD2: review responses, timeline fix, tests and final docs"
git push origin main
# эхлээд байгаа tag-уудыг харна:
git tag --list
# энэ tag нэр ашиглагдаагүй бол:
git tag v2.0.0
git push origin v2.0.0
git rev-parse HEAD
```

GitHub repository → Releases → Draft a new release → tag `v2.0.0` → title `V-1-9 BD2 Final` → release notes-д P4/P8/P5, тестийн нотолгоо нэмээд нийтэлнэ. Хэрэв default branch `main` биш бол өөрийн branch-ийг ашигла. Бусад гишүүний өөрчлөлт байвал түрүүлж татаж merge/PR хийнэ. `v2.0.0` таарахгүй бол баг бодит сул tag сонгоно.

## 4. PDF болон Poster-ийг нэг хувилбарт оруулах

- `FINAL_REPORT_DRAFT.md`-д бодит tag/hash, гишүүдийн бүртгэл нэмсний дараа PDF-г дахин экспортлоно.
- Poster дээрх QR одоогоор **repository** руу заана. Үүнийг **жинхэнэ GitHub Final Release URL** руу сольж, 1 нүүр poster-ийг дахин PDF болгоно.
- `Final Report`, `README`, `GitHub release` дээр **ижил tag/commit** байх ёстой.
- Team-ийн Final Report/Manual/Poster файлын нэрийг зохих кодоор нэрлэнэ.

## 5. MS Teams → “БД2 - Final projects”

**Постын гарчиг:** `[V-1-9] – Designing a Thread Pool • Final`

**Постын тайлбар (эцэслэсний дараа ашиглах):**

> БД1-ийн Thread Pool төсөлд өгсөн review-ийн мөрөөр (1) 4000 event-ээс хамаарахгүй зөв timeline, (2) 150 мс snapshot polling ба incremental UI, (3) FAILED/CANCELLED/STOPPED-ийн өнгө, (4) нэмэлт FIFO/concurrency тестүүдийг сайжрууллаа. Үндсэн 7 тест, timeline regression, БД2-ын 5 шинэ тест, GUI smoke test Linux/Xvfb дээр тэнцсэн. Poster, Final Report, Manual хавсаргав. GitHub Final Release: [бодит release URL].

Эцсийн poster нэг нүүр, бодит screenshot, release QR; Final Report reviewer response хүснэгт; Manual хэрэглэх заавар; release ажилладаг кодтой байх ёстой.
