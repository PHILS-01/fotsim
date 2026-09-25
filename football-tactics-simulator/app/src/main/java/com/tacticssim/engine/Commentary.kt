package com.tacticssim.engine

import com.tacticssim.model.CommentaryLanguage
import com.tacticssim.model.Side
import kotlin.random.Random

data class CommentaryLine(val minute: Int, val second: Int, val text: String)

/**
 * Converts MatchEvents into analytical, tactics-flavored commentary lines in
 * the chosen [language] (see Settings on the setup screen). Not every event
 * produces a line (passes/shots/tackles/throw-ins are throttled to avoid
 * noise); goals/corners/free-kicks/kickoff/halftime always do.
 */
class CommentaryGenerator(
    private val homeName: String,
    private val awayName: String,
    private val language: CommentaryLanguage = CommentaryLanguage.DEFAULT,
    seed: Long = System.currentTimeMillis()
) {
    private val rng = Random(seed)
    private var ticksSinceFlavorLine = 0
    private val pack = CommentaryLinePacks.forLanguage(language)

    private fun teamName(side: Side) = if (side == Side.HOME) homeName else awayName

    fun forEvent(event: MatchEvent): CommentaryLine? {
        val text: String? = when (event.type) {
            EventType.KICKOFF -> pack.kickoff.format(homeName, awayName)
            EventType.HALFTIME -> pack.halftime
            EventType.FULLTIME -> pack.fulltime
            EventType.GOAL -> pack.goal.random(rng).format(teamName(event.side))
            EventType.SHOT -> if (rng.nextFloat() < 0.6f) pack.shot.random(rng).format(teamName(event.side)) else null
            EventType.SAVE -> pack.save.random(rng).format(teamName(event.side))
            EventType.TACKLE -> if (rng.nextFloat() < 0.5f) pack.tackle.random(rng).format(teamName(event.side)) else null
            EventType.CHANCE_CREATED -> if (rng.nextFloat() < 0.7f) pack.chance.random(rng).format(teamName(event.side)) else null
            EventType.CORNER -> pack.corner.random(rng).format(teamName(event.side))
            EventType.THROW_IN -> if (rng.nextFloat() < 0.45f) pack.throwIn.random(rng).format(teamName(event.side)) else null
            EventType.FREE_KICK -> pack.freeKick.random(rng).format(teamName(event.side))
            EventType.PASS -> {
                ticksSinceFlavorLine++
                if (ticksSinceFlavorLine >= 60 && rng.nextFloat() < 0.25f) {
                    ticksSinceFlavorLine = 0
                    pack.pass.random(rng).format(teamName(event.side))
                } else null
            }
            EventType.VAR_REVIEW -> pack.varReview.format(teamName(event.side))
            EventType.GOAL_DISALLOWED -> pack.goalDisallowed.format(teamName(event.side))
        }
        return text?.let { CommentaryLine(event.minute, event.second, it) }
    }
}

/** One language's full set of commentary line templates. `%s` placeholders are
 * filled with a team name via String.format (kickoff takes two: home, away). */
private data class CommentaryLinePack(
    val kickoff: String,
    val halftime: String,
    val fulltime: String,
    val goal: List<String>,
    val shot: List<String>,
    val save: List<String>,
    val tackle: List<String>,
    val chance: List<String>,
    val pass: List<String>,
    val corner: List<String>,
    val throwIn: List<String>,
    val freeKick: List<String>,
    val varReview: String,
    val goalDisallowed: String
)

private object CommentaryLinePacks {
    fun forLanguage(language: CommentaryLanguage): CommentaryLinePack = when (language) {
        CommentaryLanguage.ENGLISH -> ENGLISH
        CommentaryLanguage.SPANISH -> SPANISH
        CommentaryLanguage.FRENCH -> FRENCH
        CommentaryLanguage.GERMAN -> GERMAN
        CommentaryLanguage.PORTUGUESE -> PORTUGUESE
        CommentaryLanguage.ITALIAN -> ITALIAN
    }

    private val ENGLISH = CommentaryLinePack(
        kickoff = "Kickoff! %s vs %s gets underway.",
        halftime = "Halftime. Teams switch ends — watch how the tactical picture flips with them.",
        fulltime = "Full time.",
        goal = listOf(
            "GOAL! %s find the breakthrough — the shape opened up at exactly the right moment.",
            "GOAL for %s! That's a chance built patiently, not by accident.",
            "It's in! %s convert — watch how the space was created before the finish."
        ),
        shot = listOf(
            "%s go for goal — decent xG on that one given the angle.",
            "Effort from %s, straight at the keeper. Low-value chance, more a pressure release.",
            "%s test the goalkeeper from distance — the shape behind the ball looks committed."
        ),
        save = listOf(
            "Good save! The %s goalkeeper holds firm.",
            "Well saved by %s — that stays out."
        ),
        tackle = listOf(
            "Dispossessed! %s win it back with a well-timed challenge.",
            "%s cut the passing lane and regain possession.",
            "Turnover — %s pounce on a loose touch."
        ),
        chance = listOf(
            "That's a real chance created by %s — good movement into the box.",
            "%s find a teammate in a dangerous central area."
        ),
        pass = listOf(
            "Switch of play from %s, looking to stretch the back line.",
            "%s work it through midfield, patient in possession.",
            "Third-man run from %s opens a passing lane down the channel.",
            "%s build from the back, inviting the press."
        ),
        corner = listOf(
            "Corner to %s — the ball's sent behind for a set-piece in the box.",
            "%s win a corner kick — good chance to load bodies into the area."
        ),
        throwIn = listOf(
            "Out of play — throw-in to %s.",
            "%s to restart with a throw down the line."
        ),
        freeKick = listOf(
            "Free kick to %s — the referee's whistle goes for a foul.",
            "%s win a free kick in a useful area."
        ),
        varReview = "The referee is going to the monitor — VAR review for a possible goal, %s.",
        goalDisallowed = "No goal! VAR overturns it — the effort from %s is chalked off."
    )

    private val SPANISH = CommentaryLinePack(
        kickoff = "¡Saque inicial! %s contra %s, comienza el partido.",
        halftime = "Descanso. Los equipos cambian de lado — atentos a cómo cambia el panorama táctico.",
        fulltime = "Final del partido.",
        goal = listOf(
            "¡GOL! %s encuentra el camino — el espacio se abrió en el momento justo.",
            "¡GOL de %s! Una jugada construida con paciencia, no por casualidad.",
            "¡La mete dentro! %s marca — miren cómo se generó el espacio antes del remate."
        ),
        shot = listOf(
            "%s remata a portería — un xG decente dado el ángulo.",
            "Disparo de %s, directo al portero. Ocasión de poco valor, más un alivio de presión.",
            "%s prueba al portero desde lejos — el bloque detrás del balón se ve comprometido."
        ),
        save = listOf(
            "¡Gran parada! El portero de %s se mantiene firme.",
            "Bien atajado por %s — se queda fuera."
        ),
        tackle = listOf(
            "¡Recuperación! %s gana el balón con una entrada perfecta.",
            "%s corta la línea de pase y recupera la posesión.",
            "Pérdida — %s aprovecha un mal control."
        ),
        chance = listOf(
            "Ocasión clara creada por %s — buen movimiento hacia el área.",
            "%s encuentra a un compañero en una zona peligrosa."
        ),
        pass = listOf(
            "Cambio de juego de %s, buscando estirar la defensa rival.",
            "%s trabaja el balón por el mediocampo con paciencia.",
            "Triangulación de %s abre un carril de pase por la banda.",
            "%s construye desde atrás, invitando a la presión rival."
        ),
        corner = listOf(
            "Córner para %s — el balón sale por la línea de fondo para un balón parado en el área.",
            "%s gana un tiro de esquina — buena oportunidad para cargar el área."
        ),
        throwIn = listOf(
            "Balón fuera — saque de banda para %s.",
            "%s reanuda con un saque de banda."
        ),
        freeKick = listOf(
            "Tiro libre para %s — el árbitro pita falta.",
            "%s gana un tiro libre en una zona peligrosa."
        ),
        varReview = "El árbitro va al monitor — revisión del VAR por un posible gol de %s.",
        goalDisallowed = "¡No hay gol! El VAR lo anula — la jugada de %s queda invalidada."
    )

    private val FRENCH = CommentaryLinePack(
        kickoff = "Coup d'envoi ! %s contre %s, le match commence.",
        halftime = "Mi-temps. Les équipes changent de côté — voyez comme le tableau tactique s'inverse.",
        fulltime = "Coup de sifflet final.",
        goal = listOf(
            "BUT ! %s trouve la faille — l'espace s'est ouvert au bon moment.",
            "BUT pour %s ! Une action construite patiemment, pas par hasard.",
            "Il est dedans ! %s marque — regardez comment l'espace a été créé avant la finition."
        ),
        shot = listOf(
            "%s tente sa chance — un xG correct vu l'angle.",
            "Frappe de %s, droit dans les gants du gardien. Occasion de faible valeur.",
            "%s teste le gardien de loin — le bloc derrière le ballon semble engagé."
        ),
        save = listOf(
            "Belle parade ! Le gardien de %s reste solide.",
            "Bien arrêté par %s — ça reste dehors."
        ),
        tackle = listOf(
            "Perte de balle ! %s récupère avec un tacle bien placé.",
            "%s coupe la ligne de passe et récupère le ballon.",
            "Perte de balle — %s profite d'un mauvais contrôle."
        ),
        chance = listOf(
            "Belle occasion créée par %s — bon mouvement vers la surface.",
            "%s trouve un coéquipier dans une zone dangereuse."
        ),
        pass = listOf(
            "Changement d'aile de %s, pour étirer la défense adverse.",
            "%s fait circuler le ballon au milieu, patient dans la possession.",
            "Le une-deux de %s ouvre un couloir de passe.",
            "%s relance depuis l'arrière, invitant le pressing adverse."
        ),
        corner = listOf(
            "Corner pour %s — le ballon sort pour un coup de pied arrêté dans la surface.",
            "%s obtient un corner — bonne occasion de charger la surface."
        ),
        throwIn = listOf(
            "Sortie de balle — touche pour %s.",
            "%s relance avec une touche le long de la ligne."
        ),
        freeKick = listOf(
            "Coup franc pour %s — l'arbitre siffle faute.",
            "%s obtient un coup franc dans une zone intéressante."
        ),
        varReview = "L'arbitre va consulter l'écran — revue VAR pour un but possible de %s.",
        goalDisallowed = "Pas de but ! La VAR l'annule — l'action de %s est refusée."
    )

    private val GERMAN = CommentaryLinePack(
        kickoff = "Anstoß! %s gegen %s, das Spiel läuft.",
        halftime = "Halbzeit. Die Mannschaften wechseln die Seiten — sehen Sie, wie sich das taktische Bild dreht.",
        fulltime = "Schlusspfiff.",
        goal = listOf(
            "TOR! %s findet die Lücke — der Raum öffnete sich genau im richtigen Moment.",
            "TOR für %s! Eine geduldig herausgespielte Chance, kein Zufall.",
            "Drin ist er! %s trifft — sehen Sie, wie der Raum vor dem Abschluss entstand."
        ),
        shot = listOf(
            "%s schließt ab — ordentlicher xG-Wert bei diesem Winkel.",
            "Abschluss von %s, direkt in die Arme des Torwarts. Wenig gefährlicher Versuch.",
            "%s prüft den Torwart aus der Distanz — die Ordnung hinter dem Ball wirkt entschlossen."
        ),
        save = listOf(
            "Starke Parade! Der Torwart von %s hält sicher.",
            "Gut pariert von %s — der Ball bleibt draußen."
        ),
        tackle = listOf(
            "Ballverlust! %s gewinnt den Ball mit einem gut getimten Tackling zurück.",
            "%s schneidet den Passweg ab und erobert den Ball.",
            "Ballverlust — %s nutzt einen schlechten Ballkontakt."
        ),
        chance = listOf(
            "Echte Chance von %s — gute Bewegung in den Strafraum.",
            "%s findet einen Mitspieler in gefährlicher zentraler Position."
        ),
        pass = listOf(
            "Seitenwechsel von %s, um die gegnerische Abwehrkette auseinanderzuziehen.",
            "%s spielt geduldig durchs Mittelfeld.",
            "Doppelpass von %s öffnet eine Passgasse.",
            "%s baut von hinten auf und lädt zum Pressing ein."
        ),
        corner = listOf(
            "Ecke für %s — der Ball geht ins Aus für einen Standard im Strafraum.",
            "%s erkämpft sich einen Eckball — gute Gelegenheit, den Strafraum zu füllen."
        ),
        throwIn = listOf(
            "Ball im Aus — Einwurf für %s.",
            "%s setzt mit einem Einwurf entlang der Linie fort."
        ),
        freeKick = listOf(
            "Freistoß für %s — der Schiedsrichter pfeift Foul.",
            "%s erkämpft sich einen Freistoß in gefährlicher Position."
        ),
        varReview = "Der Schiedsrichter geht zum Monitor — VAR-Überprüfung wegen eines möglichen Tores von %s.",
        goalDisallowed = "Kein Tor! Der VAR nimmt es zurück — der Treffer von %s zählt nicht."
    )

    private val PORTUGUESE = CommentaryLinePack(
        kickoff = "Pontapé de saída! %s contra %s, a partida começa.",
        halftime = "Intervalo. As equipes trocam de lado — veja como o quadro tático se inverte.",
        fulltime = "Fim de jogo.",
        goal = listOf(
            "GOL! %s encontra o caminho — o espaço se abriu no momento certo.",
            "GOL do %s! Uma jogada construída com paciência, não por acaso.",
            "Está dentro! %s marca — veja como o espaço foi criado antes da finalização."
        ),
        shot = listOf(
            "%s finaliza — xG decente considerando o ângulo.",
            "Finalização de %s, direto nas mãos do goleiro. Chance de baixo valor.",
            "%s testa o goleiro de longe — a marcação atrás da bola parece comprometida."
        ),
        save = listOf(
            "Grande defesa! O goleiro do %s se mantém firme.",
            "Bem defendida por %s — fica de fora."
        ),
        tackle = listOf(
            "Recuperação! %s ganha a bola com um desarme bem cronometrado.",
            "%s corta a linha de passe e recupera a posse.",
            "Perda de bola — %s aproveita um mau controle."
        ),
        chance = listOf(
            "Chance clara criada por %s — boa movimentação até a área.",
            "%s encontra um companheiro em zona perigosa."
        ),
        pass = listOf(
            "Troca de lado de %s, buscando esticar a defesa adversária.",
            "%s trabalha a bola no meio-campo com paciência.",
            "Triangulação de %s abre um corredor de passe.",
            "%s constrói a jogada desde trás, convidando a pressão adversária."
        ),
        corner = listOf(
            "Escanteio para %s — a bola sai pela linha de fundo para uma bola parada na área.",
            "%s ganha um escanteio — boa chance de lotar a área."
        ),
        throwIn = listOf(
            "Bola fora — arremesso lateral para %s.",
            "%s reinicia com um arremesso lateral."
        ),
        freeKick = listOf(
            "Falta para %s — o árbitro assinala a infração.",
            "%s ganha uma falta em zona perigosa."
        ),
        varReview = "O árbitro vai ao monitor — revisão do VAR para um possível gol do %s.",
        goalDisallowed = "Sem gol! O VAR anula — o lance do %s é invalidado."
    )

    private val ITALIAN = CommentaryLinePack(
        kickoff = "Calcio d'inizio! %s contro %s, si comincia.",
        halftime = "Intervallo. Le squadre cambiano campo — osservate come si ribalta il quadro tattico.",
        fulltime = "Triplice fischio finale.",
        goal = listOf(
            "GOL! %s trova il varco — lo spazio si è aperto nel momento giusto.",
            "GOL del %s! Un'azione costruita con pazienza, non per caso.",
            "È dentro! %s segna — guardate come si è creato lo spazio prima della conclusione."
        ),
        shot = listOf(
            "%s ci prova — xG discreto vista l'angolazione.",
            "Conclusione di %s, centrale tra le braccia del portiere. Occasione di poco valore.",
            "%s impegna il portiere dalla distanza — il blocco dietro il pallone sembra convinto."
        ),
        save = listOf(
            "Gran parata! Il portiere del %s resta in piedi.",
            "Ben parato da %s — resta fuori."
        ),
        tackle = listOf(
            "Palla persa! %s recupera con un intervento perfetto.",
            "%s chiude la linea di passaggio e recupera palla.",
            "Palla persa — %s approfitta di un controllo sbagliato."
        ),
        chance = listOf(
            "Occasione vera creata dal %s — bel movimento verso l'area.",
            "%s trova un compagno in una zona centrale pericolosa."
        ),
        pass = listOf(
            "Cambio di gioco del %s, per allargare la difesa avversaria.",
            "%s lavora il pallone a centrocampo con pazienza.",
            "Triangolazione del %s apre un corridoio di passaggio.",
            "%s costruisce dal basso, invitando il pressing avversario."
        ),
        corner = listOf(
            "Calcio d'angolo per %s — palla out sul fondo per un piazzato in area.",
            "%s conquista un corner — buona occasione per affollare l'area."
        ),
        throwIn = listOf(
            "Palla fuori — rimessa laterale per %s.",
            "%s riparte con una rimessa laterale."
        ),
        freeKick = listOf(
            "Calcio di punizione per %s — l'arbitro fischia fallo.",
            "%s conquista una punizione in zona pericolosa."
        ),
        varReview = "L'arbitro va a rivedere l'azione al monitor — revisione VAR per un possibile gol del %s.",
        goalDisallowed = "Niente gol! Il VAR lo annulla — la rete del %s viene invalidata."
    )
}
