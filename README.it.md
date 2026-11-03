[English](README.md) | Italiano

# WeatherClockWidget

Widget per la schermata Home di Android che combina orologio, condizioni meteo correnti e previsioni. L'app include una schermata di configurazione con anteprima dal vivo dell'aspetto del widget.

Il progetto è sviluppato in Kotlin, senza librerie di terze parti per la UI oltre ai componenti AndroidX e Material.

## Stato del progetto

Questo repository è un esperimento personale realizzato con un approccio di vibe coding: sviluppo rapido e iterativo assistito da modelli linguistici, con verifica manuale del risultato. Non è un prodotto commerciale, non è pensato per la produzione e non dispone di un processo di supporto.

Il software viene fornito così com'è, senza garanzie di correttezza, stabilità o idoneità a uno scopo specifico. Non sono previsti supporto, tempi di risposta o impegno a correggere problemi.

Issue, segnalazioni e pull request possono non essere lette, gestite o accettate. Chi rileva un bug o ha bisogno di una modifica è invitato a creare un fork del repository, applicare la correzione nel proprio fork e mantenerlo autonomamente.

## Funzionalità

- Widget ridimensionabile con layout dedicati in base allo spazio disponibile:
  - 1x1: orologio in alto e meteo in basso
  - 2x1: orologio, icona e temperatura
  - 4x1: orologio a sinistra, meteo allineato a destra
  - 2x2: orologio, temperatura e icone delle previsioni
  - 4x2: orologio, data, località, min/max e striscia delle previsioni
  - dimensione massima: layout esteso con otto slot di previsione
- Orologio in grassetto, con formato 12 o 24 ore.
- Tap sull'orologio: apertura dell'app Orologio del dispositivo, con fallback su più app comuni.
- Tap su qualsiasi altra area del widget: apertura delle impostazioni dell'app.
- Previsioni per le prossime ore oppure per i prossimi giorni, selezionabili nelle impostazioni.
- Previsioni giornaliere con temperatura minima e massima.
- Interfaccia disponibile in italiano e inglese, con selettore Sistema / Italiano / English. Per impostazione predefinita segue la lingua di sistema.
- Ricerca città sempre disponibile, con suggerimenti automatici mentre si digita; selezionando un risultato la località viene salvata automaticamente.
- Interruttore Posizione GPS: imposta località e coordinate dalla posizione del dispositivo. Disattivandolo, la città corrente resta come località manuale. L'attivazione del GPS non forza un aggiornamento meteo.
- Colore di sfondo personalizzabile con selettore HSV, campioni predefiniti e codice esadecimale.
- Opacità dello sfondo regolabile.
- Anteprima dal vivo nelle impostazioni, aggiornata al variare di colore, opacità, formato ora e modalità previsioni.
- Cache locale dell'ultimo aggiornamento riuscito, mostrata anche in caso di errore di rete.
- Aggiornamento periodico tramite WorkManager e aggiornamento manuale dall'app.
- Messaggi di stato mostrati solo in caso di errore; gli aggiornamenti riusciti non aggiungono righe di stato.

## Requisiti

- Android API 37 o superiore.
- JDK 17.
- Android Studio recente con Android SDK Platform 37 installato.
- Gradle Wrapper incluso nel repository.

## Compilazione

Clonare il repository e usare il wrapper Gradle:

```bash
git clone https://github.com/frankborty/WeatherClockWidget.git
cd WeatherClockWidget
./gradlew assembleDebug
```

Su Windows:

```powershell
.\gradlew.bat assembleDebug
```

L'APK di debug viene generato in:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Il file `local.properties` con il percorso dell'SDK viene generato automaticamente da Android Studio e non è versionato.

## Struttura del progetto

```text
app/src/main/java/it/frankborty/weatherclockwidget/
  MainActivity.kt              Schermata di configurazione, anteprima e stato
  data/                        Preferenze, client Open-Meteo, località, repository
  domain/                      Modelli di meteo corrente, orario e giornaliero
  widget/                      Provider, renderer RemoteViews, risoluzione dimensioni
  work/                        Aggiornamenti periodici con WorkManager
```

Le risorse del widget si trovano in `app/src/main/res/layout` e gli stili grafici in `app/src/main/res/drawable`.

## Dati e privacy

I dati meteo provengono da [Open-Meteo](https://open-meteo.com/) e non richiedono chiavi API. Le coordinate della località selezionata vengono inviate ai servizi Open-Meteo per ottenere meteo e ricerca località.

L'app non include analytics, telemetria o pubblicità. Località, preferenze e ultimo snapshot meteo sono salvati localmente sul dispositivo tramite `SharedPreferences`.

Permessi dichiarati:

- `INTERNET`: recupero dei dati meteo e della ricerca località.
- `ACCESS_COARSE_LOCATION`: località approssimativa in modalità GPS.
- `ACCESS_FINE_LOCATION`: località precisa in modalità GPS.

## Limitazioni note

- Il progetto non include una suite di test automatica.
- Il layout scelto dipende dalle dimensioni in dp riportate dal launcher e può variare tra dispositivi.
- La disponibilità dell'app Orologio dipende dalle app installate sul dispositivo.
- Il progetto è ottimizzato per un uso personale e non per la distribuzione pubblica.

## Licenza

Il progetto è rilasciato nel pubblico dominio secondo la licenza Unlicense. Chiunque può usarlo, modificarlo, distribuirlo o venderlo senza obbligo di attribuzione. Il software viene fornito senza garanzie e senza alcuna responsabilità per gli autori. Il testo completo è disponibile nel file `LICENSE`.
