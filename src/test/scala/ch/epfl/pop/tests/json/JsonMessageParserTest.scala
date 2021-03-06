package ch.epfl.pop.tests.json

import ch.epfl.pop.json.JsonMessages._
import ch.epfl.pop.json.JsonUtils.{ErrorCodes, JsonMessageParserError, JsonMessageParserException, MessageContentDataBuilder}
import ch.epfl.pop.json.{Actions, Objects, _}
import spray.json._
import ch.epfl.pop.json.JsonCommunicationProtocol._
import org.scalatest.{FunSuite, Matchers}
import JsonParserTestsUtils._
import ch.epfl.pop.json.Actions.Actions
import ch.epfl.pop.json.Methods.Methods
import ch.epfl.pop.json.Objects.Objects

import scala.util.{Failure, Success, Try}


class JsonMessageParserTest extends FunSuite with Matchers {


  implicit class RichJsonMessage(m: JsonMessage) {
    def shouldBeEqualUntilMessageContent(o: JsonMessage): Unit = {

      // Note: this function is useful because ScalaTest cannot compare List of arrays of bytes :/
      // thus we can't test two messages by using "m1 should equal (m2)"

      @scala.annotation.tailrec
      def checkListOfByteArray(l1: List[Array[Byte]], l2: List[Array[Byte]]): Unit = {
        l1.length should equal (l2.length)

        (l1, l2) match {
          case _ if l1.isEmpty =>
          case (h1 :: tail1, h2 :: tail2) =>
            h1 should equal (h2)
            checkListOfByteArray(tail1, tail2)
        }
      }

      @scala.annotation.tailrec
      def checkListOfKeySigPair(l1: List[KeySignPair], l2: List[KeySignPair]): Unit = {
        l1.length should equal (l2.length)

        (l1, l2) match {
          case _ if l1.isEmpty =>
          case (h1 :: tail1, h2 :: tail2) =>
            h1.witness should equal (h2.witness)
            h1.signature should equal (h2.signature)
            checkListOfKeySigPair(tail1, tail2)
        }
      }


      val m_1 = this.m
      val m_2 = o

      m_1 match {
        case _: PropagateMessageServer =>
          m_2 shouldBe a [PropagateMessageServer]

          val cm_1 = m_1.asInstanceOf[PropagateMessageServer]
          val cm_2 = m_2.asInstanceOf[PropagateMessageServer]

          val a1 = CreateLaoMessageClient(cm_1.params, -1, cm_1.method, cm_1.jsonrpc)
          val a2 = CreateLaoMessageClient(cm_2.params, -1, cm_2.method, cm_2.jsonrpc)

          a1 shouldBeEqualUntilMessageContent a2


        case _: JsonMessagePublishClient =>
          m_2 shouldBe a [JsonMessagePublishClient]

          val cm_1 = m_1.asInstanceOf[JsonMessagePublishClient]
          val cm_2 = m_2.asInstanceOf[JsonMessagePublishClient]

          cm_1.jsonrpc should equal(cm_2.jsonrpc)
          cm_1.id should equal(cm_2.id)
          cm_1.method should equal(cm_2.method)

          cm_1.params.channel should equal (cm_2.params.channel)
          (cm_1.params.message, cm_2.params.message) match {
            case (None, None) =>

            case (Some(mc1), Some(mc2)) =>
              mc1.sender should equal (mc2.sender)
              mc1.signature should equal (mc2.signature)
              mc1.message_id should equal (mc2.message_id)
              checkListOfKeySigPair(mc1.witness_signatures, mc2.witness_signatures)

              mc1.data._object should equal (mc2.data._object)
              mc1.data.action should equal (mc2.data.action)
              mc1.data.id should equal (mc2.data.id)
              mc1.data.name should equal (mc2.data.name)
              mc1.data.creation should equal (mc2.data.creation)
              mc1.data.last_modified should equal (mc2.data.last_modified)
              mc1.data.organizer should equal (mc2.data.organizer)
              checkListOfByteArray(mc1.data.witnesses, mc2.data.witnesses)
              mc1.data.message_id should equal (mc2.data.message_id)
              mc1.data.signature should equal (mc2.data.signature)
              mc1.data.location should equal (mc2.data.location)
              mc1.data.start should equal (mc2.data.start)
              mc1.data.end should equal (mc2.data.end)
              mc1.data.extra should equal (mc2.data.extra)

            case _ => fail()
          }

        case _ => throw new UnsupportedOperationException
      }
    }
  }


  @scala.annotation.tailrec
  final def listStringify(value: List[KeySignPair], acc: String = ""): String = {
    if (value.nonEmpty) {
      var sep = ""
      if (value.length > 1) sep = ","

      listStringify(
        value.tail,
        acc + "{\"signature\":\"" + encodeBase64String(value.head.signature.map(_.toChar).mkString) +
        "\",\"witness\":\"" + encodeBase64String(value.head.witness.map(_.toChar).mkString) + "\"}" + sep
      )
    }
    else "[" + acc + "]"
  }


  test("JsonMessageParser.parseMessage|encodeMessage:CreateLaoMessageClient") {
    var source: String = embeddedMessage(dataCreateLao, channel = "/root")
    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CreateLaoMessageClient]
    spdp shouldBe a [CreateLaoMessageClient]
    sp shouldBeEqualUntilMessageContent spdp


    // no action field
    source = embeddedMessage(dataCreateLao.replaceFirst("\"action\":[^,]*,", ""), channel = "/root")
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should equal ("invalid \"MessageContentData\" : fields missing or wrongly formatted")
      case _ => fail()
    }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:UpdateLaoMessageClient") {
    var source: String = embeddedMessage(dataUpdateLao)
    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [UpdateLaoMessageClient]
    spdp shouldBe a [UpdateLaoMessageClient]
    sp shouldBeEqualUntilMessageContent spdp
    checkBogusInputs(source)


    // last modified not int value
    source = embeddedMessage(dataUpdateLao.replaceFirst("\"last_modified\":[0-9]*", "\"last_modified\":\"12\""))
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should equal ("invalid \"updateLaoProperties\" query : field \"last_modified\" missing or wrongly formatted")
      case _ => fail()
    }

    // no action field
    source = embeddedMessage(dataUpdateLao.replaceFirst("\"action\":[^,]*,", ""))
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should equal ("invalid \"MessageContentData\" : fields missing or wrongly formatted")
      case _ => fail()
    }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:BroadcastLaoMessageClient") {
    var source: String = embeddedMessage(dataBroadcastLao)
    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [BroadcastLaoMessageClient]
    spdp shouldBe a [BroadcastLaoMessageClient]
    sp shouldBeEqualUntilMessageContent spdp
    checkBogusInputs(source)


    // last modified not int value
    source = embeddedMessage(dataBroadcastLao.replaceFirst("\"last_modified\":[0-9]*", "\"last_modified\":\"12\""))
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should startWith ("invalid \"stateBroadcastLao\" query :")
      case _ => fail()
    }

    // no action field
    source = embeddedMessage(dataBroadcastLao.replaceFirst("\"action\":[^,]*,", ""))
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should equal ("invalid \"MessageContentData\" : fields missing or wrongly formatted")
      case _ => fail()
    }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:WitnessMessageMessageClient") {
    var source: String = embeddedMessage(dataWitnessMessage)
    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [WitnessMessageMessageClient]
    spdp shouldBe a [WitnessMessageMessageClient]
    sp shouldBeEqualUntilMessageContent spdp
    checkBogusInputs(source)


    // no action field
    source = embeddedMessage(dataWitnessMessage.replaceFirst("\"action\":[^,]*,", ""))
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should equal ("invalid \"MessageContentData\" : fields missing or wrongly formatted")
      case _ => fail()
    }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:CreateMeetingMessageClient") {
    // Meeting with every argument
    var data: String = dataCreateMeeting
    var sp: JsonMessage = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    var spd: String = JsonMessageParser.serializeMessage(sp)
    var spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CreateMeetingMessageClient]
    spdp shouldBe a [CreateMeetingMessageClient]
    sp shouldBeEqualUntilMessageContent spdp


    // Meeting without location
    data = data.replaceFirst(",\"location\":\"[a-zA-Z]*\"", "")
    sp = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    spd = JsonMessageParser.serializeMessage(sp)
    spdp = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CreateMeetingMessageClient]
    spdp shouldBe a [CreateMeetingMessageClient]
    sp shouldBeEqualUntilMessageContent spdp

    // Meeting without location and end
    data = data.replaceFirst(",\"end\":[0-9]*", "")
    sp = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    spd = JsonMessageParser.serializeMessage(sp)
    spdp = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CreateMeetingMessageClient]
    spdp shouldBe a [CreateMeetingMessageClient]
    sp shouldBeEqualUntilMessageContent spdp

    // Meeting without location, end and extra
    data = data.replaceFirst(",\"extra\":\"[a-zA-Z0-9_]*\"", "")
    sp = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    spd = JsonMessageParser.serializeMessage(sp)
    spdp = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CreateMeetingMessageClient]
    spdp shouldBe a [CreateMeetingMessageClient]
    sp shouldBeEqualUntilMessageContent spdp


    // Meeting without start (should not work)
    data = data.replaceFirst(",\"start\":[0-9]*", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:BroadcastMeetingMessageClient") {
    var source: String = embeddedMessage(dataBroadcastMeeting)
    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [BroadcastMeetingMessageClient]
    spdp shouldBe a [BroadcastMeetingMessageClient]
    sp shouldBeEqualUntilMessageContent spdp
    checkBogusInputs(source)


    // last modified not int value
    source = embeddedMessage(dataBroadcastMeeting.replaceFirst("\"last_modified\":[0-9]*", "\"last_modified\":\"12\""))
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should startWith ("invalid \"stateBroadcastMeeting\" query :")
      case _ => fail()
    }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:CreateRollCallMessageClient") {
    // roll call with every argument
    var data: String = dataCreateRollCall
    var sp: JsonMessage = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    var spd: String = JsonMessageParser.serializeMessage(sp)
    var spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CreateRollCallMessageClient]
    spdp shouldBe a [CreateRollCallMessageClient]
    sp shouldBeEqualUntilMessageContent spdp
    sp.asInstanceOf[CreateRollCallMessageClient].params.message match {
      case Some(mc) => mc.data.action.toString should fullyMatch regex """create"""
      case None => fail()
    }

    // roll call without description
    data = data.replaceFirst(",\"roll_call_description\":\"[a-zA-Z]*\"", "")
    sp = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    spd = JsonMessageParser.serializeMessage(sp)
    spdp = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CreateRollCallMessageClient]
    spdp shouldBe a [CreateRollCallMessageClient]
    sp shouldBeEqualUntilMessageContent spdp

    // roll call without description and scheduled
    data = data.replaceFirst(",\"scheduled\":[0-9]*", "")
    sp = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    spd = JsonMessageParser.serializeMessage(sp)
    spdp = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CreateRollCallMessageClient]
    spdp shouldBe a [CreateRollCallMessageClient]
    sp shouldBeEqualUntilMessageContent spdp

    // roll call without description, scheduled and start
    data = data.replaceFirst(",\"start\":[0-9]*", "")
    sp = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    spd = JsonMessageParser.serializeMessage(sp)
    spdp = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CreateRollCallMessageClient]
    spdp shouldBe a [CreateRollCallMessageClient]
    sp shouldBeEqualUntilMessageContent spdp

    // roll call without description, scheduled, start and location (should fail)
    var dataW: String = data.replaceFirst(",\"location\":\"[A-Za-z]*\"", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }

    // roll call without description, scheduled, start and action (should fail)
    dataW = data.replaceFirst(",\"action\":\"[A-Za-z]*\"", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:OpenRollCallMessageClient") {
    // roll call with every argument
    val data: String = dataOpenRollCall
    var sp: JsonMessage = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [OpenRollCallMessageClient]
    spdp shouldBe a [OpenRollCallMessageClient]
    sp shouldBeEqualUntilMessageContent spdp
    sp.asInstanceOf[OpenRollCallMessageClient].params.message match {
      case Some(mc) => mc.data.action.toString should fullyMatch regex """open"""
      case None => fail()
    }

    // roll call without start (should fail)
    var dataW: String = data.replaceFirst(",\"start\":[0-9]*", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }

    // roll call without id (should fail)
    dataW = data.replaceFirst(",\"id\":\"[A-Za-z0-9=+/]*\"", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }

    // roll call with invalid action (should fail)
    dataW = _dataRollCall
      .replaceFirst("F_ACTION", Actions.State.toString)
      .replaceFirst("FF_MODIFICATION", "\"start\":3000,")
    JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
      case Right(e) =>
        e.description should equal (s"""invalid roll call query : action "${Actions.State.toString}" is unrecognizable""")
      case _ => fail()
    }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:ReopenRollCallMessageClient") {
    // Note: this message doesn't really exit. It is part of "OpenRollCallMessageClient"

    // roll call with every argument
    val data: String = dataReopenRollCall
    var sp: JsonMessage = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [OpenRollCallMessageClient]
    spdp shouldBe a [OpenRollCallMessageClient]
    sp shouldBeEqualUntilMessageContent spdp
    sp.asInstanceOf[OpenRollCallMessageClient].params.message match {
      case Some(mc) => mc.data.action.toString should fullyMatch regex """reopen"""
      case None => fail()
    }

    // roll call without start (should fail)
    var dataW: String = data.replaceFirst(",\"start\":[0-9]*", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }

    // roll call without id (should fail)
    dataW = data.replaceFirst(",\"id\":\"[A-Za-z0-9=+/]*\"", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:CloseRollCallMessageClient") {
    // roll call with every argument
    val data: String = dataCloseRollCall
    var sp: JsonMessage = JsonMessageParser.parseMessage(embeddedMessage(data)) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [CloseRollCallMessageClient]
    spdp shouldBe a [CloseRollCallMessageClient]
    sp shouldBeEqualUntilMessageContent spdp
    sp.asInstanceOf[CloseRollCallMessageClient].params.message match {
      case Some(mc) => mc.data.action.toString should fullyMatch regex """close"""
      case None => fail()
    }

    // roll call without start (should fail)
    var dataW: String = data.replaceFirst(",\"start\":[0-9]*", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }

    // roll call without end (should fail)
    dataW = data.replaceFirst(",\"end\":[0-9]*", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }

    // roll call without attendees (should fail)
    dataW = data.replaceFirst(",\"attendees\":\\[[A-Za-z0-9,\"=]*\\]", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }

    // roll call without id (should fail)
    dataW = data.replaceFirst(",\"id\":\"[A-Za-z0-9=+/]*\"", "")
    try {
      sp = JsonMessageParser.parseMessage(embeddedMessage(dataW)) match {
        case Left(_) => fail()
        case Right(_) => throw JsonMessageParserException("")
      }
    }
    catch { case _: JsonMessageParserException => }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:PropagateMessageServer") {
    val source: String = s"""{
                            |    "jsonrpc": "2.0",
                            |    "method": "broadcast",
                            |    "params": {
                            |        "channel": "channel_id",
                            |        "message": $MessageContentExample
                            |    }
                            |  }
                            |""".stripMargin.filterNot((c: Char) => c.isWhitespace)

    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [PropagateMessageServer]
    spdp shouldBe a [PropagateMessageServer]
    sp shouldBeEqualUntilMessageContent spdp
    checkBogusInputs(source)
  }

  test("JsonMessageParser.parseMessage|encodeMessage:SubscribeMessageClient") {
    val source: String = """{
                           |    "jsonrpc": "2.0",
                           |    "method": "subscribe",
                           |    "params": {
                           |        "channel": "channel_id"
                           |    },
                           |    "id": 3
                           |  }
                           |""".stripMargin.filterNot((c: Char) => c.isWhitespace)

    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    assert(sp === spdp)
    assert(sp.isInstanceOf[SubscribeMessageClient])
    assert(spdp.isInstanceOf[SubscribeMessageClient])
    checkBogusInputs(source)
  }

  test("JsonMessageParser.parseMessage|encodeMessage:UnsubscribeMessageClient") {
    val source: String = """{
                           |    "jsonrpc": "2.0",
                           |    "method": "unsubscribe",
                           |    "params": {
                           |        "channel": "channel_id"
                           |    },
                           |    "id": 3
                           |  }
                           |""".stripMargin.filterNot((c: Char) => c.isWhitespace)

    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    assert(sp === spdp)
    assert(sp.isInstanceOf[UnsubscribeMessageClient])
    assert(spdp.isInstanceOf[UnsubscribeMessageClient])
    checkBogusInputs(source)
  }

  test("JsonMessageParser.parseMessage|encodeMessage:CatchupMessageClient") {
    val source: String = """{
                           |    "jsonrpc": "2.0",
                           |    "method": "catchup",
                           |    "params": {
                           |        "channel": "channel_id"
                           |    },
                           |    "id": 3
                           |  }
                           |""".stripMargin.filterNot((c: Char) => c.isWhitespace)

    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    assert(sp === spdp)
    assert(sp.isInstanceOf[CatchupMessageClient])
    assert(spdp.isInstanceOf[CatchupMessageClient])
    checkBogusInputs(source)
  }

  test("JsonMessageParser.parseMessage|encodeMessage:AnswerResultIntMessageServer") {
    val source: String = embeddedServerAnswer(Some(0), None, id = 13)
    val sp: JsonMessage = JsonMessageParser.parseMessage(source) match {
      case Left(m) => m
      case _ => fail()
    }

    val spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [AnswerResultIntMessageServer]
    spdp shouldBe a [AnswerResultIntMessageServer]
    assertResult(source)(spd)
    checkBogusInputs(source)
  }

  test("JsonMessageParser.parseMessage|encodeMessage:AnswerResultArrayMessageServer") {
    var source: String = s"""{
                            |    "id": 99,
                            |    "jsonrpc": "2.0",
                            |    "result": F_MESSAGES
                            |  }
                            |""".stripMargin.filterNot((c: Char) => c.isWhitespace)

    val sourceFull: String = source.replaceFirst("F_MESSAGES", "[]")
    var sp: JsonMessage = JsonMessageParser.parseMessage(sourceFull) match {
      case Left(m) => m
      case _ => fail()
    }

    var spd: String = JsonMessageParser.serializeMessage(sp)
    val spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
      case Left(m) => m
      case _ => fail()
    }

    sp shouldBe a [AnswerResultArrayMessageServer]
    spdp shouldBe a [AnswerResultArrayMessageServer]
    spd should equal (sourceFull)


    // 1 message and empty witness list
    val data: MessageContentData = new MessageContentDataBuilder().setHeader(Objects.Message, Actions.Witness).setId("2".getBytes).setStart(22L).build()
    val encodedData: Base64String = JsonUtils.ENCODER.encode(data.toJson.compactPrint.getBytes).map(_.toChar).mkString
    var m: MessageContent = MessageContent(encodedData, data, "skey".getBytes, "sign".getBytes, "mid".getBytes, List())
    sp = AnswerResultArrayMessageServer(99, List(m))
    spd = JsonMessageParser.serializeMessage(sp)

    val rd: String = """eyJvYmplY3QiOiJtZXNzYWdlIiwiYWN0aW9uIjoid2l0bmVzcyIsImlkIjoiTWc9PSIsInN0YXJ0IjoyMn0="""
    var r: String = s"""[{"data":"$rd","message_id":"bWlk","sender":"c2tleQ==","signature":"c2lnbg==","witness_signatures":[]}]"""

    assertResult(source.replaceFirst("F_MESSAGES", r))(spd)
    assert(rd === JsonUtils.ENCODER.encode(data.toJson.toString().getBytes).map(_.toChar).mkString)
    assert(JsonUtils.DECODER.decode(rd).map(_.toChar).mkString === data.toJson.toString())


    // 1 message and non-empty witness list
    val sig: List[KeySignPair] = List(
      KeySignPair("ceb_prop_1".getBytes, "ceb_1".getBytes),
      KeySignPair("ceb_prop_2".getBytes, "ceb_2".getBytes),
      KeySignPair("ceb_prop_3".getBytes, "ceb_3".getBytes)
    )
    m = MessageContent(encodedData, data, "skey".getBytes, "sign".getBytes, "mid".getBytes, sig)
    sp = AnswerResultArrayMessageServer(99, List(m))
    spd = JsonMessageParser.serializeMessage(sp)

    r = s"""[{"data":"$rd","message_id":"bWlk","sender":"c2tleQ==","signature":"c2lnbg==","witness_signatures":${listStringify(sig)}}]"""

    assertResult(source.replaceFirst("F_MESSAGES", r))(spd)
    assert(rd === JsonUtils.ENCODER.encode(data.toJson.toString().getBytes).map(_.toChar).mkString)
    assert(JsonUtils.DECODER.decode(rd).map(_.toChar).mkString === data.toJson.toString())
  }

  test("JsonMessageParser.parseMessage|encodeMessage:AnswerErrorMessageServer") {
    val source: String = s"""{
                            |    "error": {
                            |       "code": ERR_CODE,
                            |       "description": "err"
                            |    },
                            |    ID
                            |    "jsonrpc": "2.0"
                            |  }
                            |""".stripMargin.filterNot((c: Char) => c.isWhitespace)

    for (i <- -5 until 0) {
      val sourceErrorCode: String = source.replaceFirst("ERR_CODE", String.valueOf(i))
      val sourceSomeId: String = sourceErrorCode.replaceFirst("ID", "\"id\":" + String.valueOf(3 * i) + ",")
      val sourceNoneId: String = sourceErrorCode.replaceFirst("ID", "\"id\":null,")

      var sp: JsonMessage = JsonMessageParser.parseMessage(sourceSomeId) match {
        case Left(m) => m
        case _ => fail()
      }

      var spd: String = JsonMessageParser.serializeMessage(sp)
      var spdp: JsonMessage = JsonMessageParser.parseMessage(spd) match {
        case Left(m) => m
        case _ => fail()
      }

      assertResult(sourceSomeId)(spd)
      sp shouldBe a [AnswerErrorMessageServer]
      spdp shouldBe a [AnswerErrorMessageServer]
      assertResult(sourceSomeId)(spd)
      checkBogusInputs(sourceSomeId)


      sp = JsonMessageParser.parseMessage(sourceNoneId) match {
        case Left(m) => m
        case _ => fail()
      }

      spd = JsonMessageParser.serializeMessage(sp)
      spdp = JsonMessageParser.parseMessage(spd) match {
        case Left(m) => m
        case _ => fail()
      }

      assertResult(sourceNoneId)(spd)
      sp shouldBe a [AnswerErrorMessageServer]
      spdp shouldBe a [AnswerErrorMessageServer]
      assertResult(sourceNoneId)(spd)
      checkBogusInputs(sourceNoneId)
    }

    // If error has invalid id (not JsNull or JsNumber) => should fail
    val sCode: String = source.replaceFirst("ERR_CODE", "-3")
    var s: String = sCode.replaceFirst("ID", "\"id\":\"12\",")
    var e = the [JsonMessageParserException] thrownBy s.parseJson.convertTo[AnswerErrorMessageServer]
    e.getMessage should equal ("invalid \"AnswerErrorMessageServer\" : id field wrongly formatted")
    s = sCode.replaceFirst("ID", "\"id\":[12],")
    e = the [JsonMessageParserException] thrownBy s.parseJson.convertTo[AnswerErrorMessageServer]
    e.getMessage should equal ("invalid \"AnswerErrorMessageServer\" : id field wrongly formatted")
    s = sCode.replaceFirst("ID", "\"id\":{\"id\":12},")
    e = the [JsonMessageParserException] thrownBy s.parseJson.convertTo[AnswerErrorMessageServer]
    e.getMessage should equal ("invalid \"AnswerErrorMessageServer\" : id field wrongly formatted")

    // If error has wrong type (not JsObject) => should fail
    val errorMatcherRegex: String = "\"error\":\\{[^\\}]*\\}"
    val sourceErrorCode: String = source.replaceFirst("ID", "\"id\":123,")

    var sourceWrongErrorType: String = sourceErrorCode.replaceFirst(errorMatcherRegex, "\"error\":10")
    JsonMessageParser.parseMessage(sourceWrongErrorType) match {
      case Right(e) =>
        e.description should equal ("invalid message : message contains a \"error\" field, but its type is unknown")
      case _ => fail()
    }

    e = the [JsonMessageParserException] thrownBy sourceWrongErrorType.parseJson.convertTo[AnswerErrorMessageServer]
    e.getMessage should equal ("invalid \"AnswerErrorMessageServer\" : fields missing or wrongly formatted")

    sourceWrongErrorType = sourceErrorCode.replaceFirst(errorMatcherRegex, "\"error\":\"10\"")
    JsonMessageParser.parseMessage(sourceWrongErrorType) match {
      case Right(e) =>
        e.description should equal ("invalid message : message contains a \"error\" field, but its type is unknown")
      case _ => fail()
    }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:\"bogus answer message\"") {
    val source: String = s"""{
                            |    "error": {
                            |       "code": ERR_CODE,
                            |       "description": "err"
                            |    },
                            |    ID
                            |    "jsonrpc": "2.0",
                            |    "result": 0
                            |  }
                            |""".stripMargin.filterNot((c: Char) => c.isWhitespace)

    for (i <- -20 to 20) {
      val sourceErrorCode: String = source.replaceFirst("ERR_CODE", String.valueOf(i))
      val sourceSomeId: String = sourceErrorCode.replaceFirst("ID", "\"id\":" + String.valueOf(3 * i) + ",")
      val sourceNoneId: String = sourceErrorCode.replaceFirst("ID", "\"id\":null,")

      var sp: JsonMessageParserError = JsonMessageParser.parseMessage(sourceSomeId) match {
        case Right(m) => m
        case _ => fail()
      }
      sp shouldBe a [JsonMessageParserError]

      sp = JsonMessageParser.parseMessage(sourceNoneId) match {
        case Right(m) => m
        case _ => fail()
      }
      sp shouldBe a [JsonMessageParserError]
    }
  }

  test("JsonMessageParser.parseMessage:Exceptions") {
    var source: String = embeddedMessage(dataOpenRollCallBuggy)
    val expected: JsonMessageParserError = JsonMessageParserError(ErrorCodes.InvalidData.toString, Some(0), ErrorCodes.InvalidData)
    val sp: JsonMessageParserError = JsonMessageParser.parseMessage(source) match {
      case Right(e) => e
      case _ => fail()
    }

    sp shouldBe a [JsonMessageParserError]
    sp should equal (expected)


    source = s"""{
                |    "jsonrpc": "2.0",
                |    "id": 12345
                |  }
                |""".stripMargin.filterNot((c: Char) => c.isWhitespace)

    JsonMessageParser.parseMessage(source) match {
      case Right(e) => e.description should equal ("invalid message : fields missing or wrongly formatted")
      case _ => fail()
    }


    // invalid message content type
    source = "{\"data\":\"myData\"}"
    val e = the [JsonMessageParserException] thrownBy source.parseJson.convertTo[MessageContent]
    e.getMessage should equal ("invalid \"MessageContent\" : fields missing or wrongly formatted")


    // invalid (object, action) pair
    val wrongAction: String = Actions.Open.toString
    source = embeddedMessage(dataWitnessMessage.replaceFirst("\"action\":[^,]*", s""""action":"$wrongAction""""))
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should equal (s"""invalid message : invalid (object = ${Objects.Message.toString}, action = $wrongAction) pair""")
      case _ => fail()
    }


    // no MessageContent for a publish method
    source = """{"jsonrpc":"2.0","method":"publish","params":{"channel":"/root/lao_id"},"id":0}"""
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should equal ("missing MessageContent in MessageParameter for JsonMessagePublishClient")
      case _ => fail()
    }


    // no params for a publish method but id readable
    val newId: Int = 678
    source = s"""{"jsonrpc":"2.0","method":"publish","id":$newId}"""
    JsonMessageParser.parseMessage(source) match {
      case Right(e) =>
        e.description should equal ("invalid MessageParameters : fields missing or wrongly formatted")
        e.id should equal (Some(newId))
      case _ => fail()
    }
  }

  test("JsonMessageParser.encodeMessage:Exceptions") {
    val sp: JsonMessage = null

    try {
      JsonMessageParser.serializeMessage(sp)
      fail()
    } catch {
      case e: SerializationException => e.getMessage should equal ("Json serializer failed : invalid input message")
      case _: Throwable => fail()
    }
  }

  test("JsonMessageParser.parseMessage|encodeMessage:Enumerations") {

    def quoteIfy(str: String): String = s""""$str""""
    val UNKNOWN_ENUM_VALUE: String = quoteIfy("string-not-in-enum")

    Methods.values.forall(v => {
      val padded = quoteIfy(v.toString)
      val converted = padded.parseJson.convertTo[Methods]
      val reversed  = converted.toJson

      reversed shouldBe a [JsString]
      reversed.toString should equal (padded)
      true
    })

    Try(UNKNOWN_ENUM_VALUE.parseJson.convertTo[Methods]) match {
      case Success(_) => fail()
      case Failure(e) =>
        e shouldBe a [DeserializationException]
        e.getMessage should equal ("invalid \"method\" field : unrecognized")
      case _ => fail()
    }


    Objects.values.forall(v => {
      val padded = quoteIfy(v.toString)
      val converted = padded.parseJson.convertTo[Objects]
      val reversed  = converted.toJson

      reversed shouldBe a [JsString]
      reversed.toString should equal (padded)
      true
    })

    Try(UNKNOWN_ENUM_VALUE.parseJson.convertTo[Objects]) match {
      case Success(_) => fail()
      case Failure(e) =>
        e shouldBe a [DeserializationException]
        e.getMessage should equal ("invalid \"object\" field : unrecognized")
      case _ => fail()
    }


    Actions.values.forall(v => {
      val padded = quoteIfy(v.toString)
      val converted = padded.parseJson.convertTo[Actions]
      val reversed  = converted.toJson

      reversed shouldBe a [JsString]
      reversed.toString should equal (padded)
      true
    })

    Try(UNKNOWN_ENUM_VALUE.parseJson.convertTo[Actions]) match {
      case Success(_) => fail()
      case Failure(e) =>
        e shouldBe a [DeserializationException]
        e.getMessage should equal ("invalid \"action\" field : unrecognized")
      case _ => fail()
    }
  }
}
