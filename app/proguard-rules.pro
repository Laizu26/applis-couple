# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Modèles Firestore : désérialisés par réflexion (toObject()), il ne faut ni les renommer
# ni supprimer leurs champs/constructeurs.
-keep class com.ensemble.app.data.model.** { *; }

# Widget Glance : instancié par nom de classe depuis le manifest/XML du provider.
-keep class com.ensemble.app.widget.** { *; }
