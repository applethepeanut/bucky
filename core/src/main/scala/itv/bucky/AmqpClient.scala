package itv.bucky

import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.StrictLogging
import itv.bucky.decl.{Binding, Exchange, Queue}
import itv.contentdelivery.lifecycle.Lifecycle

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

trait AmqpClient {

  def publisherOf[T](builder: PublishCommandBuilder[T], timeout: Duration = FiniteDuration(10, TimeUnit.SECONDS))
                    (implicit executionContext: ExecutionContext): Lifecycle[Publisher[T]] =
    publisher(timeout).map(AmqpClient.publisherOf(builder))

  def publisher(timeout: Duration = FiniteDuration(10, TimeUnit.SECONDS)): Lifecycle[Publisher[PublishCommand]]

  def consumer(queueName: QueueName, handler: Handler[Delivery], exceptionalAction: ConsumeAction = DeadLetter)
              (implicit executionContext: ExecutionContext): Lifecycle[Unit]

  def performOps(thunk: AmqpOps => Try[Unit]): Try[Unit]

}

trait AmqpOps {
  def declareQueue(queue: Queue): Try[Unit]
  def declareExchange(echange: Exchange): Try[Unit]
  def bindQueue(binding: Binding): Try[Unit]
}


object AmqpClient extends StrictLogging {

  def publisherOf[T](commandBuilder: PublishCommandBuilder[T])(publisher: Publisher[PublishCommand])
                    (implicit executionContext: ExecutionContext): Publisher[T] = (message: T) =>
    for {
      publishCommand <- Future {
        commandBuilder.toPublishCommand(message)
      }
      _ <- publisher(publishCommand)
    } yield ()


  def deliveryHandlerOf[T](handler: Handler[T], unmarshaller: DeliveryUnmarshaller[T], unmarshalFailureAction: ConsumeAction = DeadLetter)
                          (implicit ec: ExecutionContext): Handler[Delivery] =
    new DeliveryUnmarshalHandler[T, ConsumeAction](unmarshaller)(handler, unmarshalFailureAction)

  def handlerOf[T](handler: Handler[T], unmarshaller: PayloadUnmarshaller[T], unmarshalFailureAction: ConsumeAction = DeadLetter)
                  (implicit ec: ExecutionContext): Handler[Delivery] =
    deliveryHandlerOf(handler, Unmarshaller.toDeliveryUnmarshaller(unmarshaller), unmarshalFailureAction)

}

