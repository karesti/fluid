package me.escoffier.fluid.constructs.impl;

import io.reactivex.Flowable;
import io.reactivex.Single;
import me.escoffier.fluid.constructs.*;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class DataStreamImplTest {


  @Test
  public void testTransformation() {
    ListSink<Integer> list = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .transformPayload(i -> i + 1)
      .to(list);

    assertThat(list.values()).containsExactly(2, 3, 4, 5, 6);
  }

  @Test
  public void testTransformationFlow() {
    ListSink<Integer> list = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .transformPayloadFlow(flow -> flow.map(i -> i + 1))
      .to(list);

    assertThat(list.values()).containsExactly(2, 3, 4, 5, 6);
  }

  @Test
  public void testTransformer() {
    ListSink<Integer> list = Sink.list();
    Source.from(1, 2, 3, 4, 5)
      .transformPayloadFlow(flow -> flow.map(i -> i + 1))
      .to(list);
    assertThat(list.values()).containsExactly(2, 3, 4, 5, 6);
  }

  @Test
  public void testThatWeCanRetrieveTheFlow() {
    List<Integer> list = Source.from(1, 2, 3, 4, 5)
      .flow()
      .map(Data::payload)
      .toList()
      .blockingGet();
    assertThat(list).containsExactly(1, 2, 3, 4, 5);
  }

  @Test
  public void testMergeWith() {
    Source<String> s1 = Source.from("a", "b", "c");
    Source<String> s2 = Source.from("d", "e", "f");
    Source<String> s3 = Source.from("g", "h");

    ListSink<String> list = Sink.list();
    s1.mergeWith(s2).to(list);

    assertThat(list.values()).containsExactly("a", "b", "c", "d", "e", "f");

    Random random = new Random();
    ListSink<String> list2 = Sink.list();
    s1.transformFlow(flow ->
      flow.delay(s ->
        Single.just(s)
          .delay(random.nextInt(10), TimeUnit.MILLISECONDS)
          .toFlowable()))
      .mergeWith(s2, s3)
      .to(list2);

    await().atMost(1, TimeUnit.MINUTES).until(() -> list2.values().size() == 8);
    assertThat(list2.values()).contains("a", "b", "c", "d", "e", "f", "g", "h");
  }

  @Test
  public void testConcatWith() {
    Source<String> s1 = Source.from("a", "b", "c");
    Source<String> s2 = Source.from("d", "e", "f");
    Source<String> s3 = Source.from("g", "h");

    ListSink<String> list = Sink.list();
    s1.concatWith(s2).to(list);

    assertThat(list.values()).containsExactly("a", "b", "c", "d", "e", "f");

    ListSink<String> list2 = Sink.list();
    s1.transformFlow(flow ->
      flow.delay(s ->
        Single.just(s)
          .delay(10, TimeUnit.MILLISECONDS)
          .toFlowable())
    )
      .concatWith(s2, s3)
      .to(list2);

    await().atMost(1, TimeUnit.MINUTES).until(() -> list2.values().size() == 8);
    assertThat(list2.values()).contains("a", "b", "c", "d", "e", "f", "g", "h");
  }

  @Test
  public void testZipWithAnotherStream() {
    Source<String> s1 = Source.from("a", "b", "c");
    Source<String> s2 = Source.from("d", "e", "f");
    Source<String> s3 = Source.from("g", "h", "i");

    ListSink<String> list = Sink.list();
    s1.transformPayloadFlow(s -> s.map(String::toUpperCase))
      .zipWith(s2)
      .transformPayload(pair -> pair.left() + "x" + pair.right())
      .to(list);

    assertThat(list.values()).containsExactly("Axd", "Bxe", "Cxf");

    list = Sink.list();
    s1.transformPayloadFlow(s -> s.map(String::toUpperCase))
      .zipWith(s2, s3)
      .transformPayload(tuple -> tuple.nth(0) + "x" + tuple.nth(1) + "x" + tuple.nth(2))
      .to(list);

    assertThat(list.values()).containsExactly("Axdxg", "Bxexh", "Cxfxi");
  }

  @Test
  public void testDataTransformation() {
    ListSink<Integer> sink = new ListSink<>();
    Source.from(Flowable.range(0, 10).map(i -> random()))
      .transform(data -> data.with((int) (data.payload() * 100)))
      .to(sink);
    assertThat(sink.values()).hasSize(10);
    assertThat(sink.data()).hasSize(10);

    for (Data<Integer> d : sink.data()) {
      assertThat(d.<Long>get("X-Timestamp")).isNotNull().isGreaterThanOrEqualTo(0);
      assertThat(d.<Boolean>get("Random")).isTrue();
      assertThat(d.payload()).isGreaterThanOrEqualTo(0).isLessThan(100);
    }
  }


  private static Data<Double> random() {
    return new Data<>(Math.random()).with("X-Timestamp", System.currentTimeMillis()).with("Random", true);
  }


}
