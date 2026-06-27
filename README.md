# open-data-scavenger-kt
# Testskottet: Från kosmiska viskningar till lokala väderkontroller

Det började som ett enkelt "testskott" för att utforska modern utveckling på JVM:en med Kotlin, men det utvecklades snabbt till ett personligt astrofotograf-dashboard. Det här projektet väver samman trådar från rymdsonder miljarder kilometer bort, flygplanen ovanför våra huvud, och det lokala vädret utanför fönstret.

Du hittar även den fullständiga artikeln på [metricspace.se/kotlin.html](https://metricspace.se/kotlin.html).

---

## 1. Skönheten i rymdens "bloatade" XML
När man bygger mot NASA:s öppna API för *Deep Space Network (DSN)* möts man först av en till synes repetitiv och tung XML-ström. I en värld som till stor del har gått över till JSON är det lätt att avfärda formatet som föråldrat.

Men när man i stället inser vad datan faktiskt representerar ändras perspektivet. DSN-antennerna i Madrid, Goldstone och Canberra lyssnar efter signaler från farkoster som *Voyager 1* eller *New Horizons*. Signalerna som når jorden är svagare än en viskning genom solsystemet – ofta bara en bråkdel av en attowatt. I en sådan miljö finns det noll tolerans för tvetydighet eller korrupt data. Den strikta, explicita strukturen i XML-datat är inte bloat; det är digital ingenjörskonst byggd för extrem robusthet under kosmiska förhållanden.

> 📺 **Fördjupning:** > Hur Deep Space Network lyckas fånga upp dessa mikroskopiska rymdviskningar förklaras fantastiskt väl i den här videon från *Sixty Symbols*:
> 
> https://www.youtube.com/watch?v=YBaBlWv5klk

---

## 2. Det barometriska mysteriet på Arlanda
Under utvecklingen av flygradsfunktionen (som strömmar livedata från OpenSky Network över en 11x11 mils radie) dök ett märkligt fenomen upp. Ett Norwegian-plan som stod helt stilla på Arlanda rapporterade en höjd på 15 meter. Hur kan ett parkerat flygplan sväva 15 meter upp i luften med noll i hastighet?

Svaret visade sig ligga i hur flygplan mäter höjd. ADS-B-transpondrar sänder ut en *barometrisk höjd* baserad på ett internationellt standardtryck (1013,25 hPa) för att plan i luften ska kunna separeras säkert. Men Arlanda ligger 42 meter över havet. Genom att ta hänsyn till det lokala meteorologiska högtrycket (hämtat via SMHI:s API) och räkna baklänges med tryckskillnaden (där 1 hPa motsvarar cirka 8,32 meter), landade kalkylen exakt på de 15 metrarna. Mysteriet var löst: planet stod stadigt på asfalten, men barometern reagerade på dagens vackra högtrycksväder.

---

## 3. Arkitekturen: Modern pragmatism på JVM:en
Projektet är byggt på en modern JVM-stack med Kotlin (konfigurerat för Java 25). Att röra sig i Kotlins ekosystem efter många år med äldre språk ger en enorm känsla av pragmatism. Saker som har gjort utvecklingen till en fröjd:

* **Extension Functions:** Att kunna hänga på metoder som `.toCompassDirection()` eller `.validSmhiValue()` direkt på befintliga typer håller domänlogiken extremt ren.
* **Sömlös resursdelning:** Genom ren Dependency Injection delar alla tjänster (Väder, Radarn, Geokodning, DSN) på en och samma `HttpClient`, vilket optimerar connection pooling och minneshantering.
* **Tidshantering utan smärta:** Att använda `ZonedDateTime` med `withZoneSameInstant(ZoneId.systemDefault())` löser elegant problemet med att översätta SMHI:s UTC-prognoser till användarens faktiska lokala tid.

---

## Roadmap framåt: Solvinden 2026
Projektet stannar inte vid att spåra flyg och kolla molntäcke. Nästa naturliga steg för att skapa det ultimata verktyget för nattfotografering handlar om att blicka ännu högre upp.

https://youtu.be/iY_Wnj1sqI8

* **Varningar från L1:** Integrera realtidsdata från solvindsobservatorier som ACE och WIND vid Lagrangepunkt 1 för att få en 30–60 minuters förvarning innan en geomagnetisk storm träffar atmosfären och skapar norrsken (som animeras i videon ovan).
* **Omvänd siktlinje för månen:** Bygga en algoritm som räknar baklänges: *”Var och när måste jag stå inom de närmaste tre dagarna för att få en 2 % upplyst nymåne exakt ovanför Stadshusets spira?”* kombinerat med OpenStreetMap-data för att säkerställa att fotoplatsen är en tillgänglig bro eller park, och inte mitt i Riddarfjärden.

