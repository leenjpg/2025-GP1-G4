const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");

admin.initializeApp();

// دالة التجميع الأسبوعي المجدولة
exports.generateWeeklyTrends = onSchedule({
    schedule: "every sunday 23:59",
    timeZone: "Asia/Riyadh"
}, async (event) => {
    const db = admin.firestore();

    const oneWeekAgo = new Date();
    oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);

    const alertsSnapshot = await db.collection("alert")
        .where("timestamp", ">=", oneWeekAgo)
        .get();

    const totalIncidents = alertsSnapshot.size;

    // قواميس لجمع الكلمات والتطبيقات وحساب تكرارها
    let wordCounts = {};
    let appCounts = {};

    alertsSnapshot.forEach(doc => {
        const data = doc.data();
        const keywords = data.keywords || [];
        const appName = data.appName;

        // 1. حساب الكلمات
        keywords.forEach(word => {
            const cleanWord = word.trim().toLowerCase();
            if (cleanWord) {
                wordCounts[cleanWord] = (wordCounts[cleanWord] || 0) + 1;
            }
        });

        // 2. حساب التطبيقات (مع استبعاد القيم المجهولة)
        if (appName && appName !== "تطبيق آخر" && appName !== "غير معروف") {
            appCounts[appName] = (appCounts[appName] || 0) + 1;
        }
    });

    // تحويل قاموس الكلمات إلى مصفوفة وأخذ أعلى 5 كلمات
    let topWords = Object.entries(wordCounts)
        .map(([word, count]) => ({ word, count }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 5);

    // تحويل قاموس التطبيقات إلى مصفوفة وأخذ أعلى 3 تطبيقات
    let topApps = Object.entries(appCounts)
        .map(([name, count]) => ({ name, count }))
        .sort((a, b) => b.count - a.count)
        .slice(0, 3);

    const trendPayload = {
        trendId: "trend_weekly_update",
        region: "السعودية",
        ageGroupsMostAffected: "9-12, 13-15",
        cyberbullyingIncidents: totalIncidents,
        topWords: topWords,
        topApps: topApps, // الحقل الجديد الذي سيحفظ التطبيقات المتضررة
        date: admin.firestore.FieldValue.serverTimestamp()
    };

    await db.collection("trend").doc("latest_trend").set(trendPayload, { merge: true });

    console.log("تم تحديث إحصائيات الأسبوع (الكلمات والتطبيقات) بنجاح! الإجمالي:", totalIncidents);
});

// Calculate age from birth date
function calculateAge(birthDate, today) {
    let age = today.getFullYear() - birthDate.getFullYear();

    const monthDiff = today.getMonth() - birthDate.getMonth();

    if (
        monthDiff < 0 ||
        (monthDiff === 0 && today.getDate() < birthDate.getDate())
    ) {
        age--;
    }

    return age;
}

// تحديث العمر
exports.updateChildrenAges = onSchedule({
    schedule: "0 0 * * *",
    timeZone: "Asia/Riyadh"
}, async () => {
    const db = admin.firestore();

    const today = new Date(
        new Date().toLocaleDateString("en-CA", {
            timeZone: "Asia/Riyadh"
        }) + "T00:00:00"
    );

    const childrenSnapshot = await db.collection("child").get();

    const writer = db.bulkWriter();
    let updatedCount = 0;

    childrenSnapshot.forEach(doc => {
        const data = doc.data();

        // Skip old profiles without a birth date
        if (typeof data.birthDate !== "string") return;

        if (!/^\d{4}-\d{2}-\d{2}$/.test(data.birthDate)) return;

        const birthDate = new Date(data.birthDate + "T00:00:00");

        if (isNaN(birthDate.getTime()) || birthDate > today) return;

        const newAge = calculateAge(birthDate, today);

        // Update only when the age changes
        if (newAge !== data.age) {
            writer.update(doc.ref, {
                age: newAge
            });

            updatedCount++;
        }
    });

    await writer.close();

    console.log("Age updates queued:", updatedCount);
});