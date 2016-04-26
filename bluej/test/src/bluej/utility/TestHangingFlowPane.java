package bluej.utility;

import bluej.utility.javafx.HangingFlowPane;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Created by neil on 14/09/2015.
 */
public class TestHangingFlowPane
{
    // Need to run tests on FX thread:
    @Rule
    public TestRule runOnFXThreadRule = new TestRule() {
        boolean initialised = false;
        @Override public Statement apply(Statement base, Description d) {
            if (!initialised)
            {
                // Initialise JavaFX:
                new JFXPanel();
                initialised = true;
            }
            return new Statement() {
                @Override public void evaluate() throws Throwable {
                    // Run on FX thread, rethrow any exceptions back on this thread:
                    CompletableFuture<Throwable> thrown = new CompletableFuture<>();
                    Platform.runLater(() -> {
                        try
                        {
                            base.evaluate();
                            thrown.complete(null);
                        } catch (Throwable throwable)
                        {
                            thrown.complete(throwable);
                        }
                    });
                    Throwable t = thrown.get();
                    if (t != null)
                        throw t;
                }
            };
        }

    };

    private static class FixedSizeNode extends Canvas
    {
        private final Optional<Double> baseline;

        public FixedSizeNode(double width, double height)
        {
            super(width, height);
            baseline = Optional.empty();
        }

        public FixedSizeNode(double width, double height, double baseline)
        {
            super(width, height);
            this.baseline = Optional.of(baseline);
        }

        @Override
        public double getBaselineOffset()
        {
            if (baseline.isPresent())
                return baseline.get();
            else
                return super.getBaselineOffset();
        }
    }

    protected HangingFlowPane make(float width, float height, List<Node> ns)
    {
        return make(width, height, ns.toArray(new Node[0]));
    }

    protected HangingFlowPane make(float width, float height, Node... ns)
    {
        HangingFlowPane p = new HangingFlowPane(ns);
        p.setRowValignment(VPos.BASELINE);
        p.resize(width, height);
        p.requestLayout();
        p.layout();
        return p;
    }



    @Test
    public void testSingleFixed()
    {
        final FixedSizeNode n = new FixedSizeNode(100.0, 50.0);
        HangingFlowPane p = make(500, 500, n);
        assertLayout(n, 0, 0, 100, 50);
    }

    @Test
    public void testTripleFixed()
    {
        final FixedSizeNode n0 = new FixedSizeNode(100.0, 40.0);
        final FixedSizeNode n1 = new FixedSizeNode(90.0, 60.0);
        final FixedSizeNode n2 = new FixedSizeNode(120.0, 50.0);
        HangingFlowPane p = make(500, 500, n0, n1, n2);
        // Fixed size nodes have their baseline at the bottom, so they all align to the bottom:
        assertLayout(n0, 0, 20, 100, 40);
        assertLayout(n1, 100, 0, 90, 60);
        assertLayout(n2, 190, 10, 120, 50);
    }

    @Test
    public void testTripleFixedBaseline()
    {
        final FixedSizeNode n0 = new FixedSizeNode(100.0, 40.0, 35);
        final FixedSizeNode n1 = new FixedSizeNode(90.0, 60.0, 40);
        final FixedSizeNode n2 = new FixedSizeNode(120.0, 50.0, 20);
        HangingFlowPane p = make(500, 500, n0, n1, n2);
        // Baseline will be at 40, the largest baseline:
        assertLayout(n0, 0, 5, 100, 40);
        assertLayout(n1, 100, 0, 90, 60);
        assertLayout(n2, 190, 20, 120, 50);
    }

    private static class TestNodeInfo
    {
        private final double width;
        private final double expectedX;
        private final FixedSizeNode node;

        public TestNodeInfo(double width, double expectedX)
        {
            this(width, expectedX, false);
        }

        public TestNodeInfo(double width, double expectedX, boolean rightAlign)
        {
            this.width = width;
            this.expectedX = expectedX;
            this.node = new FixedSizeNode(width, 50);
            if (rightAlign)
                HangingFlowPane.setAlignment(node, HangingFlowPane.FlowAlignment.RIGHT);
        }

        Node getNode()
        {
            return node;
        }
    }


    private TestNodeInfo n(float width, float expectedX)
    {
        return new TestNodeInfo(width, expectedX);
    }

    private TestNodeInfo nr(float width, float expectedX)
    {
        return new TestNodeInfo(width, expectedX, true);
    }

    private <T> List<T> l(T... xs)
    {
        return Arrays.asList(xs);
    }


    // Tests given nodes on flow pane with width 500
    private void testRows500(double hang, List<List<TestNodeInfo>> nodes)
    {
        HangingFlowPane p = make(500, 500, nodes.stream().flatMap(ns -> ns.stream().map(TestNodeInfo::getNode)).collect(Collectors.toList()));
        p.setHangingIndent(hang);
        p.requestLayout();
        p.layout();

        double y = 0;
        for (List<TestNodeInfo> row : nodes)
        {
            for (TestNodeInfo n : row)
            {
                assertLayout(n.getNode(), n.expectedX, y, n.width, 50);
            }
            y += 50;
        }
    }

    @Test
    public void testRowsNoHang()
    {
        // Whether first row is 500, or just under, big item ends up on row beneath:
        testRows500(0, l(
            l(n(240, 0), n(260, 240)),
            l(n(40, 0))
            ));
        testRows500(0, l(
            l(n(240, 0), n(259, 240)),
            l(n(40, 0))
        ));

        // Just over the 500, so moves to next row:
        testRows500(0, l(
            l(n(240, 0)),
            l(n(261, 0), n(40, 261))
        ));
    }

    @Test
    public void testRowsHang()
    {
        testRows500(20, l(
            l(n(240, 0), n(260, 240)),
            l(n(40, 20))
        ));
        testRows500(20, l(
            l(n(240, 0)),
            l(n(261, 20), n(40, 281))
        ));

        // Over-large item always on row by itself:
        testRows500(20, l(
            l(n(501, 0))
        ));
        testRows500(20, l(
            l(n(1, 0)),
            l(n(501, 20))
        ));
        testRows500(20, l(
            l(n(1, 0)),
            l(n(501, 20)),
            l(n(1, 20))
        ));
    }

    @Test
    public void testRightAlign()
    {
        testRows500(20.0, l(l(n(100, 0), nr(100, 400))));

        testRows500(20.0, l(l(n(300, 0)), l(nr(300, 200))));
    }

    private static double e = 0.000001;

    private static void assertLayout(Node n, double x, double y, double width, double height)
    {
        assertEquals(x, n.getLayoutX(), e);
        assertEquals(y, n.getLayoutY(), e);
        assertEquals(width, n.getLayoutBounds().getWidth(), e);
        assertEquals(height, n.getLayoutBounds().getHeight(), e);
    }

}
