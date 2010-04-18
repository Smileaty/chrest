package jchrest.architecture;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import jchrest.lib.FileUtilities;
import jchrest.lib.ListPattern;
import jchrest.lib.ParsingErrorException;
import jchrest.lib.Pattern;

/**
 * Represents a node within the model's discrimination network.
 * Methods support learning and also display.
 *
 * @author Peter C. R. Lane
 */
public class Node {
  private static int _totalNodes = 0; //TODO: This count should be inside 'Chrest' to support multiple models
  private int _reference;
  private ListPattern _contents;
  private ListPattern _image;
  private List<Link> _children;
  private Node _followedBy;
  private Node _namedBy;

  /**
   * Empty constructor is only called to construct a new root node for the 
   * model.  
   */
  public Node (ListPattern type) {
    this (type, type);
    _totalNodes = 1;
    _reference = 0;
  }
 
  /**
   * When constructing non-root nodes in the network, the new contents and image 
   * must be defined.  Assume that the image always starts empty.
   */
  public Node (ListPattern contents, ListPattern image) {
    _reference = _totalNodes;
    _totalNodes += 1;

    _contents = contents.clone ();
    _contents.setNotFinished (); // do not allow contents to be finished
    _image = image;
    _children = new ArrayList<Link> ();
    _followedBy = null;
    _namedBy = null;
  }

  /**
   * Constructor to build a new Chrest node with given reference, contents and image.
   * Package access only, as should only be used by Chrest.java.
   */
  Node (int reference, ListPattern contents, ListPattern image) {
    _reference = reference;
    _totalNodes = Math.max (_totalNodes, reference); // make sure _totalNodes is largest sized node
    _contents = contents.clone ();
    _contents.setNotFinished (); // do not allow contents to be finished
    _image = image;
    _children = new ArrayList<Link> ();
    _followedBy = null;
    _namedBy = null;
  }

  /**
   * Accessor to reference number of node.
   */
  public int getReference () {
    return _reference;
  }

  /**
   * Accessor to contents of node.
   */
  public ListPattern getContents () {
    return _contents;
  }

  /**
   * Accessor to image of node.
   */
  public ListPattern getImage () {
    return _image;
  }

  /**
   * Accessor to children of node.
   */
  public List<Link> getChildren () {
    return _children;
  }

  void addTestLink (ListPattern test, Node child) {
    _children.add (0, new Link (test, child));
  }

  /**
   * Accessor to node that follows this node.
   */
  public Node getFollowedBy () {
    return _followedBy;
  }

  /**
   * Modify node that follows this node.
   */
  public void setFollowedBy (Node node) {
    _followedBy = node;
  }

  /**
   * Accessor to node that names this node.
   */
  public Node getNamedBy () {
    return _namedBy;
  }

  /**
   * Modify node that names this node.
   */
  public void setNamedBy (Node node) {
    _namedBy = node;
  }

  /** 
   * Compute the size of the network below the current node.
   */
  public int size () {
    int count = 1; // for self
    for (Link link : _children) {
      count += link.getChildNode().size ();
    }

    return count;
  }

  /**
   * If this node is a child node, then add its depth to depths.  
   * Otherwise, continue searching through children for the depth.
   */
  private void findDepth (int currentDepth, List<Integer> depths) {
    if (_children.isEmpty ()) {
      depths.add (currentDepth);
    } else {
      for (Link link : _children) {
        link.getChildNode().findDepth (currentDepth + 1, depths);
      }
    }
  }

  /**
   * Compute the average depth of nodes below this point.
   */
  public double averageDepth () {
    List<Integer> depths = new ArrayList<Integer> ();
    // -- find every depth
    for (Link link : _children) {
      link.getChildNode().findDepth(1, depths);
    }

    // -- compute the average of the depths
    int sum = 0;
    for (Integer depth : depths) {
      sum += depth;
    }
    if (depths.isEmpty ()) {
      return 0.0;
    } else {
      return (double)sum / (double)depths.size ();
    }
  }

  /**
   * LearnPrimitive is used to construct a test link and node containing 
   * precisely the given pattern.  It is assumed the given pattern contains 
   * a single primitive item, and is finished.
   */
  public Node learnPrimitive (Chrest model, ListPattern pattern) {
    assert (pattern.isFinished () && pattern.size () == 1);
    ListPattern contents = pattern.clone ();
    contents.setNotFinished ();
    Node child = new Node (contents, pattern);
    addTestLink (contents, child);
    model.advanceClock (model.getDiscriminationTime ());

    return child;
  }

  /**
   * addTest is used to construct a test link using the given pattern, 
   * with a new empty child node.  It is assumed the given pattern is 
   * non-empty and constitutes a valid, new test for the current Node.
   */
  private Node addTest (Chrest model, ListPattern pattern) {
    Node child = new Node (_contents.append (pattern), new ListPattern (_contents.getModality ()));
    addTestLink (pattern, child);
    model.advanceClock (model.getDiscriminationTime ());
    return child;
  }

  /**
   * extendImage is used to add new information to the node's image.
   * It is assumed the given pattern is non-empty and is a valid extension.
   */
  private Node extendImage (Chrest model, ListPattern newInformation) {
    _image = _image.append (newInformation);
    model.advanceClock (model.getFamiliarisationTime ());

    return this;
  }

  /**
   * Discrimination learning extends the LTM network by adding new 
   * nodes.
   */
  Node discriminate (Chrest model, ListPattern pattern) {
    ListPattern newInformation = pattern.remove (_contents);

    // cases 1 & 2 if newInformation is empty
    if (newInformation.isEmpty ()) {
      if (newInformation.isFinished ()) { // 1. add test for < $ >
        return addTest (model, newInformation);
      } else { // 2. no information to make a new test with
        return this;
      }
    }

    Node retrievedChunk = model.recognise (newInformation);
    if (retrievedChunk == model.getLtmByModality (pattern)) {
      // 3. if root node is retrieved, then the primitive must be learnt
      return model.getLtmByModality(newInformation).learnPrimitive (model, newInformation.getFirstItem ());
    } else if (retrievedChunk.getImage().isEmpty ()) {
      // 4. if the retrieved chunk has an empty image, then familiarisation must occur
      // to extend that image.
      return retrievedChunk.familiarise (model, newInformation);
    } else if (retrievedChunk.getImage().matches (newInformation)) {
      // 5. retrieved chunk can be used as a test
      return addTest (model, retrievedChunk.getImage ());
    } else { 
      // 6. mismatch, so use only the first item for test
      // NB: first-item must be in network as retrievedChunk was not the root node
      ListPattern firstItem = newInformation.getFirstItem ();
      firstItem.setNotFinished ();
      return addTest (model, firstItem);
    }
  }

  /**
   * Familiarisation learning extends the image in a node by adding new 
   * information from the given pattern.
   */
  Node familiarise (Chrest model, ListPattern pattern) {
    ListPattern newInformation = pattern.remove (_image);

    // cases 1 & 2 if newInformation is empty
    if (newInformation.isEmpty ()) {
      if (newInformation.isFinished ()) { // 1. add end marker
        return extendImage (model, newInformation);
      } else {
        // 2. nothing to do
        return this;
      }
    }

    Node retrievedChunk = model.recognise (newInformation);
    if (retrievedChunk == model.getLtmByModality (pattern)) {
      // 3. if root node is retrieved, first item of newInformation is an unknown primitive
      return model.getLtmByModality(pattern).learnPrimitive (model, newInformation.getFirstItem ());
    } else if (retrievedChunk.getImage().isEmpty ()) {
      // 4. the retrieved chunk is empty, so use first item to extend image
      // note: first item is known primitive, because new-information sorted to this node
      ListPattern firstItem = newInformation.getFirstItem ();
      firstItem.setNotFinished ();
      return extendImage (model, firstItem);
    } else if (retrievedChunk.getImage().matches (newInformation)) {
      // 5. retrieved chunk an be used to extend the image 
      //   -- make sure extension is not complete
      ListPattern toadd = retrievedChunk.getImage().clone ();
      toadd.setNotFinished ();
      return extendImage (model, toadd);
    } else { 
      // 6. mismatch, so only use first item to extend image
      // note: mismatch cannot be first item, because new-information sorted to this node
      ListPattern firstItem = retrievedChunk.getImage().getFirstItem ();
      firstItem.setNotFinished ();
      return extendImage (model, firstItem);
    }
  }

  /**
   * Write a description of the node to the given Writer object.
   */
  public void writeNode (Writer writer) throws IOException {
    FileUtilities.writeOpenTag (writer, "node");
    FileUtilities.writeTaggedInt (writer, "reference", _reference);

    FileUtilities.writeOpenTag (writer, "contents");
    _contents.writePattern (writer);
    FileUtilities.writeCloseTag (writer, "contents");
    FileUtilities.writeOpenTag (writer, "image");
    _image.writePattern (writer);
    FileUtilities.writeCloseTag (writer, "image");

    if (_children.isEmpty ()) {
      ; // write nothing if no children
    } else {
      FileUtilities.writeOpenTag (writer, "children");
      for (Link link : _children) {
        link.writeLink (writer);
      }
      FileUtilities.writeCloseTag (writer, "children");
    }
    if (_followedBy != null) {
      FileUtilities.writeOpenTag (writer, "followed-by");
      FileUtilities.writeTaggedInt (writer, "reference", _followedBy.getReference ());
      FileUtilities.writeCloseTag (writer, "followed-by");
    }
    if (_namedBy != null) {
      FileUtilities.writeOpenTag (writer, "named-by");
      FileUtilities.writeTaggedInt (writer, "reference", _namedBy.getReference ());
      FileUtilities.writeCloseTag (writer, "named-by");
    }
    FileUtilities.writeCloseTag (writer, "node");
    FileUtilities.writeNewLine (writer);

    // recurse, by writing the node descriptions for any child nodes
    for (Link link : _children) {
      link.getChildNode().writeNode (writer);
    }
  }

  /**
   * Read a description of a Node from the given buffered-reader, and construct 
   * a new instance of Node.
   */
  public static Node readFromFile (BufferedReader reader) throws ParsingErrorException {

    FileUtilities.acceptOpenTag (reader, "node");

    FileUtilities.acceptCloseTag (reader, "node");
    return new Node (null);
  }
}

