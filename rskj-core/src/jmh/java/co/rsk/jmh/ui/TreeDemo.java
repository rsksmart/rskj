package co.rsk.jmh.ui;

import co.rsk.jmh.sync.RskContextState;
import co.rsk.trie.TrieDTO;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.net.URL;
import java.util.Optional;

public class TreeDemo extends JPanel
        implements TreeSelectionListener {
    private final RskContextState context;
    private JEditorPane htmlPane;
    private JTree tree;
    private URL helpURL;
    private static boolean DEBUG = false;

    //Optionally play with line styles.  Possible values are
    //"Angled" (the default), "Horizontal", and "None".
    private static boolean playWithLineStyle = false;
    private static String lineStyle = "Horizontal";

    //Optionally set the look and feel.
    private static boolean useSystemLookAndFeel = false;

    public TreeDemo(RskContextState context) {
        super(new GridLayout(1,0));
        this.context = context;
        //Create the nodes.
        DefaultMutableTreeNode top =
                new DefaultMutableTreeNode("Unitrie");
        createNodes(top);

        //Create a tree that allows one selection at a time.
        tree = new JTree(top);
        tree.getSelectionModel().setSelectionMode
                (TreeSelectionModel.SINGLE_TREE_SELECTION);

        //Listen for when the selection changes.
        tree.addTreeSelectionListener(this);

        if (playWithLineStyle) {
            System.out.println("line style = " + lineStyle);
            tree.putClientProperty("JTree.lineStyle", lineStyle);
        }

        //Create the scroll pane and add the tree to it.
        JScrollPane treeView = new JScrollPane(tree);

        //Create the HTML viewing pane.
        htmlPane = new JEditorPane();
        htmlPane.setEditable(false);
        initHelp();
        JScrollPane htmlView = new JScrollPane(htmlPane);

        //Add the scroll panes to a split pane.
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(treeView);
        splitPane.setBottomComponent(htmlView);

        Dimension minimumSize = new Dimension(100, 50);
        htmlView.setMinimumSize(minimumSize);
        treeView.setMinimumSize(minimumSize);
        splitPane.setDividerLocation(100);
        splitPane.setPreferredSize(new Dimension(500, 300));

        //Add the split pane to this panel.
        add(splitPane);
    }

    /** Required by TreeSelectionListener interface. */
    public void valueChanged(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                tree.getLastSelectedPathComponent();

        if (node == null) return;

        Object nodeInfo = node.getUserObject();
        TrieDTO trie = (TrieDTO)nodeInfo;
        if(!(trie.isTerminal() || node.children().hasMoreElements())) {
            if(trie.getLeftHash()!=null) {
                node.add(new DefaultMutableTreeNode(context.getNode(trie.getLeftHash()).get()));
            } else if (trie.getLeft()!=null) {
                node.add(new DefaultMutableTreeNode(TrieDTO.decode(trie.getLeft(), context.getContext().getTrieStore())));
            }
            if(trie.getRightHash()!=null) {
                node.add(new DefaultMutableTreeNode(context.getNode(trie.getRightHash()).get()));
            } else if (trie.getRight()!=null) {
                node.add(new DefaultMutableTreeNode(TrieDTO.decode(trie.getRight(), context.getContext().getTrieStore())));
            }
        }

        htmlPane.setText(trie.toDescription());
    }

    private void initHelp() {
        String s = "TreeDemoHelp.html";
        helpURL = getClass().getResource(s);
        if (helpURL == null) {
            System.err.println("Couldn't open help file: " + s);
        } else if (DEBUG) {
            System.out.println("Help URL is " + helpURL);
        }

        htmlPane.setText("Hello");
    }

    private void createNodes(DefaultMutableTreeNode top) {
        DefaultMutableTreeNode category = null;
        final Optional<TrieDTO> root = context.getRoot();
        if (root.isPresent()) {
            category = new DefaultMutableTreeNode(root.get());
            top.add(category);
            category.add(new DefaultMutableTreeNode(context.getNode(root.get().getLeftHash()).get()));
            category.add(new DefaultMutableTreeNode(context.getNode(root.get().getRightHash()).get()));
        }
    }

    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event dispatch thread.
     */
    private static void createAndShowGUI() {
        if (useSystemLookAndFeel) {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                System.err.println("Couldn't use system look and feel.");
            }
        }
        final RskContextState context = new RskContextState();
        context.setup();
        //Create and set up the window.
        JFrame frame = new JFrame("TreeDemo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Add content to the window.
        frame.add(new TreeDemo(context));

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        //Schedule a job for the event dispatch thread:
        //creating and showing this application's GUI.
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }
}