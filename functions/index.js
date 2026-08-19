const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {onObjectFinalized} = require("firebase-functions/v2/storage");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

initializeApp();

// Déclenché à chaque nouveau message. Le contenu est chiffré côté app : cette fonction
// ne voit jamais le texte en clair, elle se contente d'avertir le destinataire.
exports.onNewMessage = onDocumentCreated(
  "couples/{coupleId}/messages/{messageId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;
    const message = snapshot.data();
    const {coupleId} = event.params;

    const db = getFirestore();
    const coupleDoc = await db.collection("couples").doc(coupleId).get();
    if (!coupleDoc.exists) return;
    const couple = coupleDoc.data();

    const recipientId = couple.user1Id === message.senderId ? couple.user2Id : couple.user1Id;
    if (!recipientId) return;

    const recipientDoc = await db.collection("users").doc(recipientId).get();
    const token = recipientDoc.data()?.fcmToken;
    if (!token) return;

    await getMessaging().send({
      token,
      notification: {
        title: "💌 Nouveau message",
        body: "Ouvre l'app pour lire ✨",
      },
    });
  }
);

// Déclenché à chaque nouvel APK déposé dans app-releases/ sur Storage. Le nom du fichier doit
// suivre le format "{versionCode}_{versionName}.apk" (ex: "2_1.1.apk") : la fonction met alors
// à jour app_meta/update toute seule, ce qui fait apparaître la bannière de mise à jour dans
// l'app. Aucune manipulation Firestore à faire à la main.
exports.onNewAppRelease = onObjectFinalized(async (event) => {
  const filePath = event.data.name;
  if (!filePath || !filePath.startsWith("app-releases/")) return;

  const fileName = filePath.split("/").pop();
  const match = fileName.match(/^(\d+)_(.+)\.apk$/);
  if (!match) {
    console.warn(
      `Nom de fichier ignoré: "${fileName}". Format attendu: {versionCode}_{versionName}.apk (ex: 2_1.1.apk)`
    );
    return;
  }

  const [, versionCode, versionName] = match;
  await getFirestore().collection("app_meta").doc("update").set({
    versionCode: parseInt(versionCode, 10),
    versionName,
    apkStoragePath: filePath,
    notes: "",
  });
});
