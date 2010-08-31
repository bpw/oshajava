package oshajava.runtime;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JPanel;

import oshajava.sourceinfo.ModuleSpec;
import prefuse.Constants;
import prefuse.Display;
import prefuse.Visualization;
import prefuse.action.ActionList;
import prefuse.action.RepaintAction;
import prefuse.action.assignment.ColorAction;
import prefuse.action.layout.graph.ForceDirectedLayout;
import prefuse.activity.Activity;
import prefuse.controls.DragControl;
import prefuse.controls.NeighborHighlightControl;
import prefuse.controls.PanControl;
import prefuse.controls.WheelZoomControl;
import prefuse.controls.ZoomControl;
import prefuse.controls.ZoomToFitControl;
import prefuse.data.Edge;
import prefuse.data.Graph;
import prefuse.data.Node;
import prefuse.data.expression.parser.ExpressionParser;
import prefuse.render.DefaultRendererFactory;
import prefuse.render.EdgeRenderer;
import prefuse.render.LabelRenderer;
import prefuse.util.ColorLib;
import prefuse.util.force.CircularWallForce;
import prefuse.visual.EdgeItem;
import prefuse.visual.VisualItem;
import prefuse.visual.expression.InGroupPredicate;

public class VisualGrapher extends StackEdgeGatherer {

	public static void main(String[] args) {
		VisualGrapher v = new VisualGrapher();
		v.test();
	}

	private volatile boolean doneTest = false;
	private void test() {
		if (!doneTest) {
			System.out.println(">HELLO");
			synchronized (commGraph) {
				Node last = commGraph.addNode();
				last.setString("name", "ROOT");
				for (int i = 1; i < 25; ++i) {
					//			try {
					//				Thread.sleep(2000);
					//			} catch (InterruptedException e) {}
					Node n = commGraph.addNode();
					n.setString("name", "Node#"+i);
					Edge e = commGraph.addEdge(last,n);
					e.setInt("type", i%3+1);
					last = n;
				}
			}
			System.out.println("<HELLO");
			doneTest = true;
		}
	}

	private static final Point CENTER_POINT = new Point(0,0);
	private static final int INIT_WIDTH = 700;
	private static final int INIT_HEIGHT = 600;

	private final JFrame frame = new JFrame("OSHAJAVA - Runtime Visualizer");
	private final Queue<PreEdge> queue = new LinkedList<PreEdge>();

	private final Visualization vis = new Visualization();
	private final Display disp = new Display(vis);
	private final Graph commGraph = new Graph(true);

	private final Map<Integer,Node> uidToNode = new HashMap<Integer,Node>();

	public VisualGrapher() {
		Logger.getLogger(ExpressionParser.class.getName()).setLevel(Level.OFF); // MUHAHAHAHAHAHAHA!

		JPanel panel = new JPanel(new BorderLayout());
		frame.add(panel);

		panel.add(disp,BorderLayout.CENTER);

		setupDisplay();
		setupGraph();
		setupVisualizer();

		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setVisible(true);
		frame.pack();
	}

	private void setupGraph() {
		commGraph.addColumn("name", String.class);
		commGraph.addColumn("type", int.class);
	}

	private void setupVisualizer() {
		vis.add("graph",commGraph);
		vis.setInteractive("graph.edges", null, false);

		DefaultRendererFactory rf = new DefaultRendererFactory(new LabelRenderer("name"));
		CurvyEdgeRenderer er = new CurvyEdgeRenderer();
		er.setDefaultLineWidth(2);
		er.setArrowHeadSize(17, 17);
		rf.add(new InGroupPredicate("graph.edges"), er);
		vis.setRendererFactory(rf);

		ActionList color = new ActionList(Activity.INFINITY);
		ColorAction text = new ColorAction("graph.nodes",
				VisualItem.TEXTCOLOR,ColorLib.color(Color.BLACK));
		color.add(text);
		// Borders in grey
		ColorAction border = new ColorAction("graph.nodes",
				VisualItem.STROKECOLOR,ColorLib.color(Color.GRAY));
		color.add(border);
		// Backgrounds in white.
		ColorAction fill = new ColorAction("graph.nodes",
				VisualItem.FILLCOLOR,ColorLib.color(Color.WHITE));
		color.add(fill);
		// Edge colors
		int[] palette = new int[] { ColorLib.color(Color.BLACK),
				ColorLib.color(Color.RED), ColorLib.color(Color.CYAN), ColorLib.color(Color.BLUE)};
		ColorAction arrows = new ColorAction("graph.edges", VisualItem.FILLCOLOR);
		ColorAction edgeStroke = new ColorAction("graph.edges", VisualItem.STROKECOLOR, ColorLib.color(Color.BLACK));
		/* Red:   runtime only
		 * Cyan: spec only
		 * Blue:  runtime & spec */
		for (int i = 0; i < palette.length; i++) {
			arrows.add("[type] = " + i, palette[i]);
			edgeStroke.add("[type] = " + i, palette[i]);
		}
		color.add(arrows);
		color.add(edgeStroke);
		vis.putAction("color", color);
		vis.run("color");

		ActionList layout = new ActionList(Activity.INFINITY);
		ForceDirectedLayout dl = new ForceDirectedLayout("graph");
		dl.getForceSimulator().getForces()[2].setParameter(1, 200); // Make spring force less intense.
		dl.getForceSimulator().addForce(new CircularWallForce(-10,0,0,500)); // Temporarily keep everything in.
		layout.add(dl);
		layout.add(new RepaintAction());
		vis.putAction("layout", layout);
		vis.run("layout");
	}

	private void setupDisplay() {
		disp.setSize(INIT_WIDTH, INIT_HEIGHT);
		disp.panTo(CENTER_POINT);

		disp.addControlListener(new DragControl());
		disp.addControlListener(new PanControl());
		disp.addControlListener(new ZoomControl());
		disp.addControlListener(new NeighborHighlightControl());
		disp.addControlListener(new WheelZoomControl());
		disp.addControlListener(new ZoomToFitControl());
	}

	public void handleEdge(ModuleSpec module, int writerID, int readerID) {
		synchronized (queue) {
			queue.add(new PreEdge(module,writerID,readerID,true));
		}
	}

	public void flushComms() {
		synchronized (queue) {
			for (PreEdge e : queue)
				e.addToGraph();
		}
	}
	//	
	//	private static class StaggeredAction {
	//		
	//		private final Runnable action;
	//		private final AtomicInteger count = new AtomicInteger(0);
	//		
	//		public StaggeredAction(Runnable action, int d) {
	//			this.action = action;
	//		}
	//		
	//		public void increment() {
	//			if (count.incrementAndGet()%d == 0) {
	//				action.run();
	//			}
	//		}
	//	}

	private class PreEdge {

		private Node writer;
		private Node reader;
		private int type;

		public PreEdge(ModuleSpec module, int writerID, int readerID, boolean runtime) {
			if (runtime) {
				type = module.isAllowed(writerID, readerID) ? 3 : 1;
			} else {
				type = 2;
			}

			if (type == 1) { // XXX Only add nodes with bad edges.
				writer = getMethodNode(module,writerID);
				reader = getMethodNode(module,readerID);
			}
		}

		private Node getMethodNode(ModuleSpec module, int methId) {
			synchronized (commGraph) {
				synchronized (uidToNode) {
					if (!uidToNode.containsKey(methId)) {
						Node n = commGraph.addNode();
						n.setString("name",module.getMethodSignature(methId));
						uidToNode.put(methId, n);
					}
				}
			}
			return uidToNode.get(methId);
		}

		public void addToGraph() {
			if (type == 1) { // XXX Only add bad edges.
				synchronized (commGraph) {
					Edge e = commGraph.getEdge(writer,reader);
					if (e == null) {
						e = commGraph.addEdge(writer, reader);
						e.setInt("type", type);
					}// else if (e.getInt("type") != type) {
					//	e.setInt("type", Math.min(type+e.getInt("type"), 3));
					//}
				}
			}
		}
	}

	private class CurvyEdgeRenderer extends EdgeRenderer {

		public CurvyEdgeRenderer() {
			super(Constants.EDGE_TYPE_CURVE);
		}

		public void getCurveControlPoints(EdgeItem eitem,
				Point2D[] cp,
				double x1,
				double y1,
				double x2,
				double y2) {
			if (eitem.getSourceNode().equals(eitem.getTargetNode())) { // Self-loops!!!
				double scale = 60;
				cp[0].setLocation(x1 - scale, y1 + scale);
				cp[1].setLocation(x2 + scale, y2 + scale);
			} else {
				super.getCurveControlPoints(eitem, cp, x1, y1, x2, y2);
			}
		}
	}
}
