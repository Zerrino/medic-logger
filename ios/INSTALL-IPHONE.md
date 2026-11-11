# Installation sur iPhone

Les utilisateurs de Medic Logger n’ont **aucune clé développeur, aucun certificat et aucun réglage de signature** à saisir. Le chiffrement est aussi automatique : l’app crée sa propre clé dans le Trousseau de l’iPhone.

La signature d’une app native est gérée du côté de l’éditeur. Envoyer un fichier `.ipa` par mail ne suffit pas à autoriser son installation. [Distribution Apple](https://help.apple.com/xcode/mac/current/en.lproj/dev31de635e5.html)

## Pour les testeurs

1. Installer **TestFlight** depuis l’App Store.
2. Ouvrir le lien d’invitation envoyé par l’éditeur.
3. Toucher **Installer** pour Medic Logger.

Les testeurs n’ont pas besoin de Mac ni de certificat. L’éditeur téléverse d’abord un build signé dans App Store Connect ; Apple examine le premier build destiné aux testeurs externes. [TestFlight](https://developer.apple.com/testflight/)

## Pour la version publique

Les utilisateurs installeront l’app depuis sa page App Store. Le compte éditeur, la signature et la publication restent du côté de l’éditeur ; ces opérations n’ont pas été effectuées dans cette livraison.

## Ce qui est prêt

- `ios/MedicLogger.xcodeproj`, scheme **MedicLogger**, version **1.4.2 (8)**.
- `artifacts/Medic-Logger-iOS-Simulator.zip` : app pour le simulateur du Mac M3, sans compte Apple Developer payant. Ce fichier ne s’installe pas sur un iPhone physique.
- `ios/build/MedicLogger.xcarchive` : archive iPhone/iPad compilée, sans signature. Après sélection de l’équipe Apple Developer pour **MedicLogger** et **MedicLoggerStatus**, Xcode peut créer l’archive signée pour distribution. Aucun secret de signature n’est inclus dans les sources. [Signature automatique](https://help.apple.com/xcode/mac/current/en.lproj/dev60b6fbbc7.html)

Pour un essai personnel, Xcode peut installer directement sur un iPhone connecté avec un compte Apple gratuit, mais le profil expire après 7 jours. Ce n’est pas la méthode retenue pour les utilisateurs de l’app. [Compte personnel Apple](https://developer.apple.com/help/account/basics/about-your-developer-account/)
