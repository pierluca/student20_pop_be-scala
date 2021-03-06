package ch.epfl.pop.tests


import ch.epfl.pop.crypto.Hash
import ch.epfl.pop.json._
import spray.json.enrichAny
import ch.epfl.pop.json.JsonCommunicationProtocol.MessageContentDataFormat
import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

object MessageCreationUtils {

  def b64Encode(b: Array[Byte]): Array[Byte] = Base64.getEncoder.encode(b)

  def b64EncodeToString(b: Array[Byte]): String = Base64.getEncoder.encodeToString(b)

  def getMessageParams(data: MessageContentData, pk: PublicKey, sk: PrivateKey, channel: ChannelName): MessageParameters = {
    val encodedData = b64Encode(data.toJson.compactPrint.getBytes).map(_.toChar).mkString
    val signature = Curve25519.sign(sk, encodedData.getBytes())
    val messageId = Hash.computeMessageId(encodedData, signature)
    val sender = supertagged.untag(pk)
    val witnessSignature: List[KeySignPair] = Nil

    val content = MessageContent(encodedData, data, sender, signature, messageId, witnessSignature)
    val params = MessageParameters(channel, Some(content))
    params
  }
}
