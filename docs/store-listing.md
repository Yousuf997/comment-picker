# Google Play store listing (draft)

Draft for Play Console, in English and Arabic (plan M-21). `[APP NAME]` stays a placeholder until the name and icon are decided (open decision 1; working name "DrawProof"). Replace it everywhere before the first upload. Limits: app name 30 characters, short description 80, full description 4,000.

## English

**App name:** [APP NAME]: Fair Giveaways

**Short description** (78 characters):

> Pick Instagram giveaway winners fairly, prove it, and keep your data on phone.

**Full description:**

> Run Instagram comment giveaways your followers can trust.
>
> [APP NAME] picks winners from the comments on your post or Reel, and gives you proof that the draw was fair. Everything happens on your phone: no sign-up, no account with us, nothing uploaded.
>
> **Fair, and anyone can check it**
> • Before entries close, you post a draw code in your caption. Anyone who saw it can later check the draw against it.
> • After the draw, the certificate shows the secret behind the code and a fingerprint of the entry list. Anyone can re-run the draw with the free, open verifier and get the same winners.
> • Every certificate is signed on your device.
>
> **Simple, step by step**
> • Pick a post or Reel.
> • Set the rules: friends to mention, a hashtag or keyword, one entry per person, a closing time, how many winners and alternates.
> • Lock in the draw and paste the code into your caption.
> • After the deadline, import the comments and review every entry, with the reason any was left out.
> • Run the draw. It's recorded as a video made for Stories and Reels, and you choose whether to save it.
> • Confirm each winner, or replace them with the next alternate. Then share the certificate to your Story.
>
> **Honest about limits**
> Instagram doesn't let apps see who follows you or liked a post, so the app asks you to check those by hand before you confirm a winner.
>
> **Private by design**
> • Your giveaways are stored only on this phone, encrypted.
> • Optional app lock with fingerprint, face or PIN.
> • Encrypted backups you control, automatic clean-up of old giveaways, and "Delete everything" in one place.
> • No ads. No analytics about you.
>
> Works with Instagram Business and Creator accounts. Signing in uses Instagram's official page; the app never sees your password and never posts, comments or sends messages.
>
> In English and Arabic.

## العربية

**اسم التطبيق:** [APP NAME]: سحوبات عادلة

**الوصف المختصر** (أقل من 80 حرفًا):

> اختر الفائزين في مسابقات إنستغرام بعدل، وأثبت ذلك، وبياناتك على هاتفك.

**الوصف الكامل:**

> أقم مسابقات التعليقات على إنستغرام بطريقة يثق بها متابعوك.
>
> يختار [APP NAME] الفائزين من التعليقات على منشورك أو مقطع الريلز، ويمنحك إثباتًا على أن السحب كان عادلًا. كل شيء يتم على هاتفك: لا تسجيل، ولا حساب لدينا، ولا يُرفع أي شيء.
>
> **عادل، ويمكن لأي شخص التحقق منه**
> • قبل إغلاق المشاركات تنشر رمز السحب في وصف المنشور، ويمكن لكل من رآه أن يطابق السحب معه لاحقًا.
> • بعد السحب تعرض الشهادة السر وراء الرمز وبصمة قائمة المشاركات، ويمكن لأي شخص إعادة السحب بالأداة المفتوحة المجانية والحصول على الفائزين أنفسهم.
> • كل شهادة موقّعة على جهازك.
>
> **بسيط وخطوة بخطوة**
> • اختر منشورًا أو مقطع ريلز.
> • حدّد الشروط: عدد الأصدقاء المطلوب ذكرهم، ووسم أو كلمة، ومشاركة واحدة لكل شخص، ووقت الإغلاق، وعدد الفائزين والبدلاء.
> • ثبّت السحب والصق الرمز في وصف المنشور.
> • بعد الموعد النهائي استورد التعليقات وراجع كل مشاركة مع سبب استبعاد أي منها.
> • أجرِ السحب. يُسجَّل كفيديو مناسب للقصص والريلز، وأنت تختار حفظه أو لا.
> • أكّد كل فائز أو استبدله بالبديل التالي، ثم شارك الشهادة في قصتك.
>
> **صريح بشأن الحدود**
> لا يسمح إنستغرام للتطبيقات بمعرفة من يتابعك أو من أعجب بالمنشور، لذلك يطلب منك التطبيق التحقق من ذلك بنفسك قبل تأكيد الفائز.
>
> **خصوصية منذ التصميم**
> • تُحفظ مسابقاتك على هذا الهاتف فقط، مشفّرة.
> • قفل اختياري للتطبيق بالبصمة أو الوجه أو رمز PIN.
> • نسخ احتياطية مشفّرة تتحكم بها، وحذف تلقائي للمسابقات القديمة، و«حذف كل شيء» في مكان واحد.
> • بلا إعلانات، وبلا تحليلات عنك.
>
> يعمل مع حسابات إنستغرام التجارية وحسابات صنّاع المحتوى. يتم تسجيل الدخول عبر صفحة إنستغرام الرسمية، ولا يرى التطبيق كلمة مرورك أبدًا، ولا ينشر أو يعلّق أو يرسل رسائل.
>
> باللغتين العربية والإنجليزية.

## Screenshots

Phone screenshots come from the screen designs (spec: Release checklist). The screenshot tests already render each screen at 390 × 844 dp; recapture them on a phone with real-looking sample data before upload. Order and source files:

| # | Screen | English | Arabic |
|---|---|---|---|
| 1 | S1 Welcome | `feature-onboarding/src/test/screenshots/s1_welcome_light.png` | `s1_welcome_arabic.png` |
| 2 | S7 Rules | `feature-create/.../s7_set_rules_light.png` | `s7_set_rules_arabic_errors.png` (recapture without errors) |
| 3 | S8 Lock in the draw | `feature-create/.../s8_lock_in_light.png` | `s8_lock_in_arabic.png` |
| 4 | S10 Review entries | `feature-create/.../s10_review_light.png` | `s10_review_arabic_sheet.png` |
| 5 | S12 Drawing | `feature-draw/.../s12_drawing.png` | `s12_drawing_arabic.png` |
| 6 | S14 Winners | `feature-draw/.../s14_winners.png` | `s14_winners_arabic.png` |
| 7 | S15 Certificate | `feature-draw/.../s15_certificate.png` | `s15_certificate_ar.png` |
| 8 | Certificate Story image | `feature-draw/.../certificate_story_en.png` | `certificate_story_ar.png` |

Feature graphic (1024 × 500) and the icon wait for the app name.

## Data safety form

Answer as the app actually behaves (spec: Privacy; plan A5, A6):

- **Data collected:** Crash logs and Diagnostics only, through Firebase Crashlytics in release builds. Not shared, not used for tracking, not linked to the user, processed ephemerally where possible. Users can't turn it off in the app, so mark it as required.
- **Not collected:** name, email, user IDs, photos, contacts, location, app activity, messages. Giveaway data (usernames and comments read from Instagram) stays on the device and never reaches us.
- **Data in transit:** the Instagram login code goes to our login helper, which exchanges it for a token, returns it and keeps nothing. Play Integrity tokens carry only hashes. All traffic is encrypted (HTTPS, certificate pinning).
- **Deletion:** users can delete everything in the app (Settings, "Delete everything"); nothing is kept on a server.
- **Security practices:** data encrypted in transit; independent security review before launch (H-05).

## Before submitting

- App name and icon decided; `[APP NAME]` replaced here and in `core-designsystem` strings.
- Privacy policy URL live (`giveaway.privacyPolicyUrl`) and linked in Play Console and in the Meta app.
- Content rating questionnaire: no user-generated content shared through the app, no ads, no purchases.
- Target audience: 18+ (business tool).
