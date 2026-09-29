const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.sendAlertNotification = onDocumentCreated("alert/{alertId}", async (event) => {
    const snap = event.data;
    const alertId = event.params.alertId;

    if (!snap) {
        console.log("No snapshot data.");
        return null;
    }

    const alert = snap.data();
    const parentId = alert.parentId;

    console.log("New alert received:", alertId, "for parent:", parentId);

    if (!parentId) {
        console.log("No parentId found in alert. Exiting.");
        return null;
    }

    const db = getFirestore();

    const parentDoc = await db.collection("parent").doc(parentId).get();
    const token = parentDoc.data()?.fcmToken;

    if (!token) {
        console.log("No FCM token found for parent:", parentId);
        return null;
    }

    let childName = "الطفل";
    if (alert.childId) {
        const childDoc = await db.collection("child").doc(alert.childId).get();
        childName = childDoc.data()?.childName || childName;
    }

    const message = {
        token: token,
        notification: {
            title: "🚨 تنبيه حماية",
            body: "تم اكتشاف محتوى من " + childName
        },
        data: {
            alertId: String(alertId),
            type: String(alert.type || "unknown"),
            childName: String(childName),
            confidence: String(alert.confidence || 0)
        },
        android: {
            priority: "high",
            notification: {
                channelId: "hami_alerts",
                sound: "default"
            }
        }
    };

    try {
        const response = await getMessaging().send(message);
        console.log("Notification sent successfully:", response);
        return response;
    } catch (error) {
        console.error("Error sending notification:", error);
        return null;
    }
});