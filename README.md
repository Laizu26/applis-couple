# Ensemble

Application Android privée pour couple à distance — juste pour vous deux.

## Fonctionnalités

- **Messages chiffrés** : chat en temps réel, chiffré de bout en bout (AES-256-GCM). Firebase ne voit jamais le contenu en clair.
- **Compte à rebours** : jusqu'à votre prochaine rencontre, avec un titre personnalisable.
- **Photos privées** : galerie partagée, chaque photo est chiffrée avant d'être envoyée.
- **Notifications push** : alerte à l'arrivée d'un nouveau message (le contenu n'apparaît jamais dans la notification, puisqu'il est chiffré).

## Comment fonctionne le chiffrement

À la création du couple, l'app génère une clé AES-256 aléatoire **uniquement sur l'appareil**. Cette clé
n'est jamais envoyée à Firebase. Elle est encodée avec l'identifiant du couple dans un **code de pairing**
que vous transmettez une seule fois à votre partenaire, par un canal sûr (en personne, appel, SMS...).
L'autre appareil colle ce code, ce qui reconstitue la même clé localement. Tous les messages, légendes de
photos et données du compte à rebours sont chiffrés avec cette clé avant d'être envoyés à Firestore/Storage.

**Important** : gardez une copie du code de pairing dans un endroit sûr (gestionnaire de mots de passe,
par exemple). En cas de réinstallation de l'app ou de changement de téléphone, il vous permettra de
retrouver l'accès à vos données sans rien perdre.

## Prérequis

- [Android Studio](https://developer.android.com/studio) (Koala ou plus récent)
- Un compte Google pour créer un projet [Firebase](https://console.firebase.google.com/)
- [Node.js](https://nodejs.org/) 20+ et `npm install -g firebase-tools` si vous voulez déployer les
  notifications push (Cloud Functions)

## 1. Créer le projet Firebase

1. Sur la [console Firebase](https://console.firebase.google.com/), créez un nouveau projet.
2. Ajoutez une application **Android** avec le nom de package `com.ensemble.app`.
3. Téléchargez le fichier `google-services.json` généré et placez-le dans `app/google-services.json`
   (ce fichier n'est pas commité dans le dépôt, c'est normal).
4. Activez les produits suivants dans la console :
   - **Authentication** → méthode de connexion **E-mail/Mot de passe**
   - **Firestore Database** → créez la base en mode production
   - **Storage** → activez le bucket par défaut
   - **Cloud Messaging** → activé automatiquement

## 2. Déployer les règles de sécurité et les notifications

```bash
firebase login
firebase use --add   # sélectionnez votre projet
firebase deploy --only firestore:rules,storage:rules
```

Les Cloud Functions (notifications push) nécessitent le plan **Blaze** (paiement à l'usage). Pour 2
utilisateurs, la consommation reste largement dans le quota gratuit (0 €/mois en pratique).

```bash
cd functions
npm install
cd ..
firebase deploy --only functions
```

Sans ce déploiement, l'app fonctionne normalement, mais vous ne recevrez pas de notification push à
l'arrivée d'un message.

## 3. Lancer l'application

Ouvrez le dossier du projet dans Android Studio, laissez-le synchroniser Gradle, puis lancez l'app sur
votre téléphone ou un émulateur.

## 4. Premier appairage

1. Le premier partenaire crée un compte (e-mail/mot de passe) puis choisit **Créer notre couple** :
   un code de pairing est généré.
2. Il transmet ce code à l'autre partenaire par un canal sûr.
3. Le second partenaire crée son compte, choisit **J'ai déjà un code**, colle le code reçu.
4. Les deux appareils sont désormais reliés, avec la même clé de chiffrement.

## Personnalisation

C'est votre app, faites-la vôtre :
- Nom affiché : `app/src/main/res/values/strings.xml` (`app_name`)
- Couleurs : `app/src/main/java/com/ensemble/app/ui/theme/Color.kt`
- Icône : `app/src/main/res/drawable/ic_launcher_foreground.xml`

## Structure du projet

```
app/           application Android (Kotlin + Jetpack Compose)
functions/     Cloud Function pour les notifications push
firestore.rules, storage.rules   règles de sécurité (accès réservé aux 2 membres du couple)
```

## Limites connues

- Le chiffrement protège le **contenu** (messages, légendes, photos). Les métadonnées (horodatage,
  qui a envoyé quoi, nombre de photos) restent visibles côté Firebase, comme sur la plupart des apps.
- Il n'y a pas de scan QR pour le pairing (juste copier-coller du code) — simple et suffisant pour un
  usage à 2 personnes.
- Pensée pour exactement 2 comptes par couple ; ce n'est pas fait pour être publié publiquement en l'état.
