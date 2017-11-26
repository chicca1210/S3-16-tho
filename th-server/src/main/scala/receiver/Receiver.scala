package receiver

import java.io.IOException

import com.rabbitmq.client.{AMQP, ConnectionFactory, DefaultConsumer, Envelope}
import dboperation._
import domain._
import domain.messages._
import play.api.libs.json.Json
import utils.RabbitInfo

trait Receiver {

    def startRecv(): Unit

    def getLastMessage: String
}

class ReceiverImpl extends Receiver {

    private var lastMessage: String = _

    @throws[Exception]
    override def startRecv(): Unit = {
        println("Receiver started")
        val factory = new ConnectionFactory
        factory setHost RabbitInfo.HOST
        factory setUsername RabbitInfo.USERNAME
        factory setPassword RabbitInfo.PASSWORD
        val connection = factory.newConnection
        val channel = connection.createChannel
        channel.queueDeclare(RabbitInfo.QUEUE_NAME, false, false, false, null)
        val consumer = new DefaultConsumer(channel) {
            @throws[IOException]
            override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]): Unit = {
                val data = new String(body, RabbitInfo.MESSAGE_ENCODING)
                println("received: " + body)
                lastMessage = data
                println("received: " + data)
                val message = Json.parse(data).as[Message]
                val sender = message.sender toInt
                val mType = message.messageType
                val payload = message.payload
                mType match {
                    case msgType.TreasureHunt => {
                        val idOrganizer = sender
                        val th = Json.parse(payload).as[TreasureHunt]
                        val treasureHuntDB: TreasureHuntDB = new TreasureHuntDBImpl
                        val thID = treasureHuntDB.insertNewTreasureHunt(th.name, th.location, th.date, th.time, idOrganizer)
                        channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, thID.toString.getBytes)
                    }
                    case msgType.ListTHs => {
                        val idOrganizer = sender
                        val treasureHuntDB = new TreasureHuntDBImpl
                        val list = treasureHuntDB.viewTreasureHuntList(idOrganizer)
                        val message: String = ListTHsMsgImpl("0", ListTHsImpl(list).defaultRepresentation).defaultRepresentation
                        channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, message.getBytes)
                    }
                    case msgType.ListPOIs => {
                        val poiDB = new POIDBImpl
                        val list = poiDB.getPOIsList(payload.toInt)
                        val message: String = new ListPOIsMsgImpl("0", new ListPOIsImpl(list).defaultRepresentation).defaultRepresentation
                        channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, message.getBytes)
                    }
                    case msgType.Poi => {
                        //TODO controllare se sto aggiungendo o eliminando il poi (in base all'id)
                        val idOrganizer = sender
                        val poi = Json.parse(payload).as[POI]
                        val newQuiz: NewQuizDB = new NewQuizDBImpl
                        val newClue: NewClueDB = new NewClueDBImpl
                        val poiDB: POIDB = new POIDBImpl

                        val idQuiz = newQuiz.insertNewQuiz(poi.quiz.question, poi.quiz.answer, idOrganizer)
                        val idClue = newClue.insertNewClue(poi.clue.content, idOrganizer)
                        val idPOI = poiDB.insertNewPOI(poi, idOrganizer)

                        poiDB.setQuiz(idPOI, idQuiz)
                        poiDB.setClue(idPOI, idClue)

                        poi.ID = idPOI
                        poi.quiz.ID = idQuiz
                        poi.clue.ID = idClue

                        val message: String = PoiMsgImpl("0", poi.defaultRepresentation).defaultRepresentation
                        channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, message.getBytes)
                    }
                    case msgType.Position => {
                        var position = Json.parse(payload).as[Position]
                        println("posirtion")
                    }
                    case _ => {
                        channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, RabbitInfo.KO_RESPONSE.getBytes)
                    }
                }
            }
        }

        channel.basicConsume(RabbitInfo.QUEUE_NAME, true, consumer)


        val channelPS = connection.createChannel
        channelPS.exchangeDeclare(RabbitInfo.EXCHANGE_NAME, "fanout")
        val queueName = channelPS.queueDeclare.getQueue
        channelPS.queueBind(queueName, RabbitInfo.EXCHANGE_NAME, "")
        val consumer2 = new DefaultConsumer(channelPS) {
            @throws[IOException]
            override def handleDelivery(consumerTag: String, envelope: Envelope, properties: AMQP.BasicProperties, body: Array[Byte]): Unit = {
                val data = new String(body, RabbitInfo.MESSAGE_ENCODING)
                lastMessage = data
                println(data)
                val message = Json.parse(data).as[Message]
                val sender = message.sender
                val mType = message.messageType
                val payload = message.payload
                mType match {
                    case msgType.Position => {
                        if (payload == "") {
                            //TODO return the first poi of the hunt
                            channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, "{\"messageType\":\"PoiMsg\",\"sender\":\"0\",\"payload\":\"{\\\"ID\\\":333,\\\"name\\\":\\\"nomePOI\\\",\\\"treasureHuntID\\\":44,\\\"position\\\":\\\"{\\\\\\\"latitude\\\\\\\":44.218699,\\\\\\\"longitude\\\\\\\":12.057483}\\\",\\\"quiz\\\":\\\"{\\\\\\\"ID\\\\\\\":0,\\\\\\\"question\\\\\\\":\\\\\\\"domanda\\\\\\\",\\\\\\\"answer\\\\\\\":\\\\\\\"risposta\\\\\\\"}\\\",\\\"clue\\\":\\\"{\\\\\\\"ID\\\\\\\":0,\\\\\\\"content\\\\\\\":\\\\\\\"indizio\\\\\\\"}\\\"}\"}".getBytes())
                        } else { //TODO find the poi with this position and send the next POI
                            var position = Json.parse(payload).as[Position]
                            channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, "{\"messageType\":\"PoiMsg\",\"sender\":\"0\",\"payload\":\"{\\\"ID\\\":333,\\\"name\\\":\\\"nomePOI\\\",\\\"treasureHuntID\\\":44,\\\"position\\\":\\\"{\\\\\\\"latitude\\\\\\\":44.218699,\\\\\\\"longitude\\\\\\\":12.057483}\\\",\\\"quiz\\\":\\\"{\\\\\\\"ID\\\\\\\":0,\\\\\\\"question\\\\\\\":\\\\\\\"domanda\\\\\\\",\\\\\\\"answer\\\\\\\":\\\\\\\"risposta\\\\\\\"}\\\",\\\"clue\\\":\\\"{\\\\\\\"ID\\\\\\\":0,\\\\\\\"content\\\\\\\":\\\\\\\"indizio\\\\\\\"}\\\"}\"}".getBytes())
                        }
                    }
                    case msgType.TreasureHunt => { // received if organizer creates a new TreasureHunt
                        var th = Json.parse(payload).as[TreasureHunt]
                        val treasureHuntDB: TreasureHuntDB = new TreasureHuntDBImpl
                        val thID = treasureHuntDB.insertNewTreasureHunt(th.name, th.location, th.date, th.time, sender.toInt)
                    }
                    case msgType.ListTHs => { // received if player require TH list
                        val idOrganizer = sender
                        val treasureHuntDB = new TreasureHuntDBImpl
                        val list = treasureHuntDB.viewTreasureHuntList(idOrganizer toInt)
                        val message: String = ListTHsMsgImpl("0", ListTHsImpl(list).defaultRepresentation).defaultRepresentation
                        channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, message.getBytes)
                    }
                    case msgType.Login => {
                        var login = Json.parse(payload).as[Login]
                        var user = login.username;
                        var pass = login.password;
                        // TODO verificare se user e pass corrispondono
                        if (true) {
                            channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, ("{\"messageType\":\"LoginMsg\",\"sender\":\"0\",\"payload\":\"" + RabbitInfo.OK_RESPONSE + "\"}").getBytes())
                        } else {
                            channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, ("{\"messageType\":\"LoginMsg\",\"sender\":\"0\",\"payload\":\"" + RabbitInfo.KO_RESPONSE + "\"}").getBytes())
                        }
                    }
                    case msgType.Registration => {
                        var reg = Json.parse(payload).as[Registration]
                        var user = reg.username;
                        var pass = reg.password;
                        // TODO verificare se esiste gia un iser con questo nome, se non c'è salvarlo
                        if (true) {
                            channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, ("{\"messageType\":\"RegistrationMsg\",\"sender\":\"0\",\"payload\":\"" + RabbitInfo.OK_RESPONSE + "\"}").getBytes())
                        } else {
                            channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, ("{\"messageType\":\"RegistrationMsg\",\"sender\":\"0\",\"payload\":\"" + RabbitInfo.KO_RESPONSE + "\"}").getBytes())
                        }
                    }
                    case _ => {
                        channel.basicPublish("", properties.getReplyTo, new AMQP.BasicProperties.Builder().correlationId(properties.getCorrelationId).build, RabbitInfo.KO_RESPONSE.getBytes)
                    }
                }
            }
        }

        channelPS.basicConsume(queueName, false, consumer2)
    }

    override def getLastMessage: String = lastMessage

}

object Server extends App {
    val receiver: Receiver = new ReceiverImpl
    receiver startRecv()
}
