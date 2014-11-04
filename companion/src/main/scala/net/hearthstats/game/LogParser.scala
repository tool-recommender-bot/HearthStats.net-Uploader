package net.hearthstats.game

import grizzled.slf4j.Logging
import net.hearthstats.game.CardEvents._

class LogParser extends Logging {
  val ZONE_PROCESSCHANGES_REGEX = """\[Zone\] ZoneChangeList\.ProcessChanges\(\) - id=(\d*) local=(.*) \[name=(.*) id=(\d*) zone=(.*) zonePos=(\d*) cardId=(.*) player=(\d*)\] zone from (.*) -> (.*)""".r
  val HIDDEN_REGEX = """\[Zone\] ZoneChangeList\.ProcessChanges\(\) - id=(\d*) local=(.*) \[id=(\d*) cardId=(.*) type=(.*) zone=(.*) zonePos=(\d*) player=(\d*)\] zone from (.*) -> (.*)""".r
  val TURN_CHANGE_REGEX = """\[Zone\] ZoneChangeList.ProcessChanges\(\) - processing index=.* change=powerTask=\[power=\[type=TAG_CHANGE entity=\[id=.* cardId= name=GameEntity\] tag=NEXT_STEP value=MAIN_ACTION\] complete=False\] entity=GameEntity srcZoneTag=INVALID srcPos= dstZoneTag=INVALID dstPos=""".r
  val HERO_POWER_USE_REGEX = """\[Power\].*cardId=(\w+).*player=(\d+).*""".r

  def analyseLine(line: String): Option[GameEvent] = {
    line match {
      case ZONE_PROCESSCHANGES_REGEX(zoneId, local, card, id, cardZone, zonePos, cardId, player, fromZone, toZone) =>
        debug(s"HS Zone uiLog: zoneId=$zoneId local=$local cardName=$card id=$id cardZone=$cardZone zonePos=$zonePos cardId=$cardId player=$player fromZone=$fromZone toZone=$toZone")
        analyseCard(cardZone, fromZone, toZone, card, cardId, player.toInt, id.toInt)
      case HIDDEN_REGEX(zoneId, local, id, cardId, _, cardZone, zonePos, player, fromZone, toZone) =>
        debug(s"HS Zone uiLog: zoneId=$zoneId local=$local cardName=HIDDEN id=$id cardZone=$cardZone zonePos=$zonePos cardId=$cardId player=$player fromZone=$fromZone toZone=$toZone")
        analyseCard(cardZone, fromZone, toZone, "", cardId, player.toInt, id.toInt)
      case TURN_CHANGE_REGEX() =>
        debug("turn passed")
        Some(TurnPassedEvent)
      case HERO_POWER_USE_REGEX(cardId, player) =>
        debug("Hero Power")
        Some(HeroPowerEvent(cardId, player.toInt))
      // Note : emitted at game start + several times at each use, need to filter !
      case _ =>
        // ignore line
        None
    }
  }

  val heroId = """HERO_(\d+)""".r

  def analyseCard(
    cardZone: String,
    fromZone: String,
    toZone: String,
    card: String,
    cardId: String,
    player: Int,
    id: Int): Option[GameEvent] =
    (cardZone, fromZone, toZone) match {
      case ("DECK", "", "FRIENDLY DECK") =>
        Some(CardAddedToDeck(card, id))
      case ("DECK", "", "OPPOSING DECK") =>
        Some(CardAddedToDeck(card, id))
      case ("DECK", "OPPOSING HAND", "OPPOSING DECK") =>
        Some(CardReplaced(card, id))
      case ("DECK", "FRIENDLY HAND", "FRIENDLY DECK") =>
        Some(CardReplaced(card, id))
      case ("HAND", "", "FRIENDLY HAND") =>
        Some(CardDrawn(card, id))
      case ("HAND", "OPPOSING DECK", "OPPOSING HAND") =>
        Some(CardDrawn(card, id))
      case ("HAND", "FRIENDLY DECK", "FRIENDLY HAND") =>
        Some(CardDrawn(card, id))
      case ("HAND", "FRIENDLY HAND", "FRIENDLY PLAY") =>
        Some(CardPlayed(card, id))
      case ("HAND", "FRIENDLY HAND", "FRIENDLY PLAY (Weapon)") =>
        Some(CardPlayed(card, id))
      case ("HAND", "FRIENDLY HAND", "FRIENDLY SECRET") =>
        Some(CardPlayed(card, id))
      case ("HAND", "FRIENDLY HAND", "") =>
        Some(CardPlayed(card, id))
      case ("HAND", "FRIENDLY PLAY", "FRIENDLY HAND") =>
        Some(CardReturned(card, id))
      case ("PLAY", "", "FRIENDLY PLAY") =>
        Some(CardPutInPlay(card, id))
      case ("PLAY", "", "FRIENDLY PLAY (Weapon)") =>
        Some(CardPutInPlay(card, id))
      case ("PLAY", "", "FRIENDLY PLAY (Hero)") =>
        val heroId(hid) = cardId
        Some(HeroChosen(card, hid.toInt, opponent = false, player))
      case ("PLAY", "", "FRIENDLY PLAY (Hero Power)") =>
        Some(HeroPowerDeclared(cardId, player))
      case ("PLAY", "", "OPPOSING PLAY") =>
        Some(CardPutInPlay(card, id))
      case ("PLAY", "", "OPPOSING PLAY (Weapon)") =>
        Some(CardPutInPlay(card, id))
      case ("PLAY", "", "OPPOSING PLAY (Hero)") =>
        val heroId(id) = cardId
        Some(HeroChosen(card, id.toInt, opponent = true, player))
      case ("PLAY", "", "OPPOSING PLAY (Hero Power)") =>
        Some(HeroPowerDeclared(cardId, player))
      case ("PLAY", "OPPOSING HAND", "OPPOSING PLAY") =>
        Some(CardPlayed(card, id))
      case ("PLAY", "OPPOSING HAND", "OPPOSING PLAY (Weapon)") =>
        Some(CardPlayed(card, id))
      case ("PLAY", "OPPOSING HAND", "") =>
        Some(CardPlayed(card, id))
      case ("SECRET", "OPPOSING DECK", "OPPOSING_SECRET") =>
        Some(CardPutInPlay(card, id))
      case ("GRAVEYARD", "", "FRIENDLY GRAVEYARD") =>
        Some(CardDiscarded(card, id))
      case ("GRAVEYARD", "", "OPPOSING GRAVEYARD") =>
        Some(CardDiscarded(card, id))
      case ("GRAVEYARD", "FRIENDLY HAND", "FRIENDLY GRAVEYARD") =>
        Some(CardDiscarded(card, id))
      case ("GRAVEYARD", "FRIENDLY PLAY", "FRIENDLY GRAVEYARD") =>
        Some(CardDestroyed(card, id))
      case ("GRAVEYARD", "FRIENDLY PLAY (Weapon)", "FRIENDLY GRAVEYARD") =>
        Some(CardDestroyed(card, id))
      case ("GRAVEYARD", "FRIENDLY PLAY (Hero)", "FRIENDLY GRAVEYARD") =>
        Some(HeroDestroyedEvent(false))
      case ("GRAVEYARD", "FRIENDLY SECRET", "FRIENDLY GRAVEYARD") =>
        Some(CardDestroyed(card, id))
      case ("GRAVEYARD", "OPPOSING HAND", "OPPOSING GRAVEYARD") =>
        Some(CardDiscarded(card, id))
      case ("GRAVEYARD", "OPPOSING PLAY", "OPPOSING GRAVEYARD") =>
        Some(CardDestroyed(card, id))
      case ("GRAVEYARD", "OPPOSING PLAY (Weapon)", "OPPOSING GRAVEYARD") =>
        Some(CardDestroyed(card, id))
      case ("GRAVEYARD", "OPPOSING PLAY (Hero)", "OPPOSING GRAVEYARD") =>
        Some(HeroDestroyedEvent(true))
      case ("GRAVEYARD", "OPPOSING SECRET", "OPPOSING GRAVEYARD") =>
        Some(CardDestroyed(card, id))
      case _ =>
        warn(s"Unhandled log for $card: zone $cardZone from $fromZone to $toZone")
        None
    }
}