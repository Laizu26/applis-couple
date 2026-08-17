const {onDocumentCreated} = require("firebase-functions/v2/firestore");
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
