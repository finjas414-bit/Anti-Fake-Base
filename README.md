# FakeBaseFilter (Fabric 1.21.11, client)

Erkennt Fake-Basen per Heuristik:
1. Unmoeglich platzierte Bloecke (Schienen, Teppiche, Fackeln, Redstone, Tueren ... ohne Untergrund) via `BlockState#canPlaceAt`
2. Frei schwebende Container (rundherum nur Luft)
3. Container mitten in natuerlicher Hoehle (viel `CAVE_AIR` im 7x7x7-Wuerfel)

Befehle: /fbf list | fake | clear | chat | float <n> | cave <n> | ratio <r>
API: FakeBaseFilterApi.isFake(cx, cz) / isRealCandidate(cx, cz)

Bauen: `gradle wrapper` (einmalig), dann `./gradlew build` -> build/libs/
Versionen in gradle.properties mit https://fabricmc.net/develop/ abgleichen.
