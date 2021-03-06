/*  Copyright (c) 2006-2007, Vladimir Nikic
    All rights reserved.

    Redistribution and use of this software in source and binary forms,
    with or without modification, are permitted provided that the following
    conditions are met:

    * Redistributions of source code must retain the above
      copyright notice, this list of conditions and the
      following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the
      following disclaimer in the documentation and/or other
      materials provided with the distribution.

    * The name of HtmlCleaner may not be used to endorse or promote
      products derived from this software without specific prior
      written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
    ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
    POSSIBILITY OF SUCH DAMAGE.

    You can contact Vladimir Nikic by sending e-mail to
    nikic_vladimir@yahoo.com. Please include the word "HtmlCleaner" in the
    subject line.
*/

package org.htmlcleaner;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * <p>
 *      XML node tag - basic node of the cleaned HTML tree. At the same time, it represents start tag token
 *      after HTML parsing phase and before cleaning phase. After cleaning process, tree structure remains
 *      containing tag nodes (TagNode class), content (text nodes - ContentNode), comments (CommentNode)
 *      and optionally doctype node (DoctypeToken).
 * </p>
 *
 * Created by: Vladimir Nikic<br/>
 * Date: November, 2006.
 */
public class TagNode extends TagToken {

    
    /**
     * All nodes.
     */
    public static class TagAllCondition implements ITagNodeCondition {
        public boolean satisfy(TagNode tagNode) {
            return true;
        }
    }

    /**
     * Checks if node has specified name.
     */
    public static class TagNodeNameCondition implements ITagNodeCondition {
        private String name;

        public TagNodeNameCondition(String name) {
            this.name = name;
        }

        public boolean satisfy(TagNode tagNode) {
            return tagNode == null ? false : tagNode.name.equalsIgnoreCase(this.name);
        }
    }

    /**
     * Checks if node contains specified attribute.
     */
    public static class TagNodeAttExistsCondition implements ITagNodeCondition {
        private String attName;

        public TagNodeAttExistsCondition(String attName) {
            this.attName = attName;
        }

        public boolean satisfy(TagNode tagNode) {
            return tagNode == null ? false : tagNode.attributes.containsKey( attName.toLowerCase() );
        }
    }

    /**
     * Checks if node has specified attribute with specified value.
     */
    public static class TagNodeAttValueCondition implements ITagNodeCondition {
        private String attName;
        private String attValue;
        private boolean isCaseSensitive;

        public TagNodeAttValueCondition(String attName, String attValue, boolean isCaseSensitive) {
            this.attName = attName;
            this.attValue = attValue;
            this.isCaseSensitive = isCaseSensitive;
        }

        public boolean satisfy(TagNode tagNode) {
            if (tagNode == null || attName == null || attValue == null) {
                return false;
            } else {
                return isCaseSensitive ?
                        attValue.equals( tagNode.getAttributeByName(attName) ) :
                        attValue.equalsIgnoreCase( tagNode.getAttributeByName(attName) );
            }
        }
    }

    /**
     * Checks if node has specified attribute with specified value.
     */
    public static class TagNodeAttNameValueRegexCondition implements ITagNodeCondition {
        private Pattern attNameRegex;
        private Pattern attValueRegex;

        public TagNodeAttNameValueRegexCondition(Pattern attNameRegex, Pattern attValueRegex) {
            this.attNameRegex = attNameRegex;
            this.attValueRegex = attValueRegex;
        }

        public boolean satisfy(TagNode tagNode) {
            if (tagNode != null ) {
                for(Map.Entry<String, String>entry: (Set<Map.Entry<String, String>>)tagNode.getAttributes().entrySet()) {
                    if ( (attNameRegex == null || attNameRegex.matcher(entry.getKey()).find()) && (attValueRegex == null || attValueRegex.matcher( entry.getValue() ).find())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private TagNode parent;
    private Map<String, String> attributes = new LinkedHashMap<String, String>();
    private List children = new ArrayList();
    private DoctypeToken docType;
    private List itemsToMove;

    private transient boolean isFormed;
    
    /**
     * Used to indicate a start tag that was auto generated because {@link TagInfo#isContinueAfter(String)}(closedTag.getName()) returned true
     * For example, 
     * <pre>
     * <b><i>foo</b>bar
     * </pre>
     * would result in a new <i> being created resulting in
     * <pre>
     * <b><i>foo</i></b><i>bar</i>
     * </pre>
     * The second opening <i> tag is marked as autogenerated. This allows the autogenerated tag to be removed if it is unneeded.
     */
    private boolean autoGenerated;
    
    /**
     * Indicates that the node was marked to be pruned out of the tree.
     */
    private boolean pruned;

	public TagNode(String name) {
        super(name == null ? null : name.toLowerCase());
    }

    /**
     * @param attName
     * @return Value of the specified attribute, or null if it this tag doesn't contain it.
     */
    public String getAttributeByName(String attName) {
		return attName != null ? (String) attributes.get(attName.toLowerCase()) : null;
	}

    /**
     * @return Map instance containing all attribute name/value pairs.
     */
    public Map<String, String> getAttributes() {
		return attributes;
	}

    public void setAttributes(Map<String, String> attributes) {
        this.attributes= attributes;
    }
    /**
     * Checks existence of specified attribute.
     * @param attName
     * @return true if TagNode has attribute
     */
    public boolean hasAttribute(String attName) {
        return attName != null ? attributes.containsKey(attName.toLowerCase()) : false;
    }

    /**
     * Adds specified attribute to this tag or overrides existing one.
     * @param attName
     * @param attValue
     */
    @Override
    public void addAttribute(String attName, String attValue) {
        if ( attName != null ) {
            String trim = attName.trim().toLowerCase();
            String value = attValue == null?"":attValue.trim().replaceAll("\\p{Cntrl}", " ");
            if ( trim.length() != 0 ) {
                attributes.put(trim, value );
            }
        }
    }

    /**
     * Removes specified attribute from this tag.
     * @param attName
     */
    public void removeAttribute(String attName) {
        if ( attName != null && !"".equals(attName.trim()) ) {
            attributes.remove( attName.toLowerCase() );
        }
    }

    /**
     * @return List of children objects. During the cleanup process there could be different kind of
     * children inside, however after clean there should be only TagNode instances.
     */
    public List getChildren() {
		return children;
	}

    void setChildren(List children) {
        this.children = children;
    }

    public List getChildTagList() {
        List childTagList = new ArrayList();
        for (int i = 0; i < children.size(); i++) {
            Object item = children.get(i);
            if (item instanceof TagNode) {
                childTagList.add(item);
            }
        }

        return childTagList;
    }

    /**
     * @return An array of child TagNode instances.
     */
    public TagNode[] getChildTags() {
        List childTagList = getChildTagList();
        TagNode childrenArray[] = new TagNode[childTagList.size()];
        for (int i = 0; i < childTagList.size(); i++) {
            childrenArray[i] = (TagNode) childTagList.get(i);
        }

        return childrenArray;
    }

    /**
     * @return Text content of this node and it's subelements.
     */
    public CharSequence getText() {
        StringBuffer text = new StringBuffer();
        for (int i = 0; i < children.size(); i++) {
            Object item = children.get(i);
            if (item instanceof ContentNode) {
                text.append( ((ContentNode)item).getContent() );
            } else if (item instanceof TagNode) {
                CharSequence subtext = ((TagNode)item).getText();
                text.append(subtext);
            }
        }

        return text;
    }

    /**
     * @return Parent of this node, or null if this is the root node.
     */
    public TagNode getParent() {
		return parent;
	}

    public DoctypeToken getDocType() {
        return docType;
    }

    public void setDocType(DoctypeToken docType) {
        this.docType = docType;
    }

    public void addChild(Object child) {
        if (child == null) {
            return;
        }
        if (child instanceof List) {
            addChildren( (List)child );
        } else if (child instanceof ProxyTagNode) {
        	children.add( ((ProxyTagNode)child).getToken() );
        } else {
            children.add(child);
            if (child instanceof TagNode) {
                TagNode childTagNode = (TagNode)child;
                childTagNode.parent = this;
            }
        }
    }

    /**
     * Add all elements from specified list to this node.
     * @param newChildren
     */
    public void addChildren(List newChildren) {
    	if (newChildren != null) {
    		Iterator it = newChildren.iterator();
    		while (it.hasNext()) {
    			Object child = it.next();
    			addChild(child);
    		}
    	}
    }

    /**
     * Finds first element in the tree that satisfy specified condition.
     * @param condition
     * @param isRecursive
     * @return First TagNode found, or null if no such elements.
     */
    private TagNode findElement(ITagNodeCondition condition, boolean isRecursive) {
        if (condition == null) {
            return null;
        }

        for (int i = 0; i < children.size(); i++) {
            Object item = children.get(i);
            if (item instanceof TagNode) {
                TagNode currNode = (TagNode) item;
                if ( condition.satisfy(currNode) ) {
                    return currNode;
                } else if (isRecursive) {
                    TagNode inner = currNode.findElement(condition, isRecursive);
                    if (inner != null) {
                        return inner;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get all elements in the tree that satisfy specified condition.
     * @param condition
     * @param isRecursive
     * @return List of TagNode instances with specified name.
     */
    public List getElementList(ITagNodeCondition condition, boolean isRecursive) {
        List result = new LinkedList();
        if (condition == null) {
            return result;
        }

        for (int i = 0; i < children.size(); i++) {
            Object item = children.get(i);
            if (item instanceof TagNode) {
                TagNode currNode = (TagNode) item;
                if ( condition.satisfy(currNode) ) {
                    result.add(currNode);
                }
                if (isRecursive) {
                    List innerList = currNode.getElementList(condition, isRecursive);
                    if (innerList != null && innerList.size() > 0) {
                        result.addAll(innerList);
                    }
                }
            }
        }

        return result;
    }

    /**
     * @param condition
     * @param isRecursive
     * @return The array of all subelements that satisfy specified condition.
     */
    private TagNode[] getElements(ITagNodeCondition condition, boolean isRecursive) {
        final List list = getElementList(condition, isRecursive);
        TagNode array[];
        if ( list == null ) {
            array = new TagNode[ 0 ];
        } else {
            array = (TagNode[]) list.toArray(new TagNode[ list.size() ]);
        }
        return array;
    }


    public List getAllElementsList(boolean isRecursive) {
        return getElementList( new TagAllCondition(), isRecursive );
    }

    public TagNode[] getAllElements(boolean isRecursive) {
        return getElements( new TagAllCondition(), isRecursive );
    }

    public TagNode findElementByName(String findName, boolean isRecursive) {
        return findElement( new TagNodeNameCondition(findName), isRecursive );
    }

    public List getElementListByName(String findName, boolean isRecursive) {
        return getElementList( new TagNodeNameCondition(findName), isRecursive );
    }

    public TagNode[] getElementsByName(String findName, boolean isRecursive) {
        return getElements( new TagNodeNameCondition(findName), isRecursive );
    }

    public TagNode findElementHavingAttribute(String attName, boolean isRecursive) {
        return findElement( new TagNodeAttExistsCondition(attName), isRecursive );
    }

    public List getElementListHavingAttribute(String attName, boolean isRecursive) {
        return getElementList( new TagNodeAttExistsCondition(attName), isRecursive );
    }

    public TagNode[] getElementsHavingAttribute(String attName, boolean isRecursive) {
        return getElements( new TagNodeAttExistsCondition(attName), isRecursive );
    }

    public TagNode findElementByAttValue(String attName, String attValue, boolean isRecursive, boolean isCaseSensitive) {
        return findElement( new TagNodeAttValueCondition(attName, attValue, isCaseSensitive), isRecursive );
    }

    public List getElementListByAttValue(String attName, String attValue, boolean isRecursive, boolean isCaseSensitive) {
        return getElementList( new TagNodeAttValueCondition(attName, attValue, isCaseSensitive), isRecursive );
    }

    public TagNode[] getElementsByAttValue(String attName, String attValue, boolean isRecursive, boolean isCaseSensitive) {
        return getElements( new TagNodeAttValueCondition(attName, attValue, isCaseSensitive), isRecursive );
    }

    /**
     * Evaluates XPath expression on give node. <br>
     * <em>
     *  This is not fully supported XPath parser and evaluator.
     *  Examples below show supported elements:
     * </em>
     * <code>
     * <ul>
     *      <li>//div//a</li>
     *      <li>//div//a[@id][@class]</li>
     *      <li>/body/*[1]/@type</li>
     *      <li>//div[3]//a[@id][@href='r/n4']</li>
     *      <li>//div[last() >= 4]//./div[position() = last()])[position() > 22]//li[2]//a</li>
     *      <li>//div[2]/@*[2]</li>
     *      <li>data(//div//a[@id][@class])</li>
     *      <li>//p/last()</li>
     *      <li>//body//div[3][@class]//span[12.2<position()]/@id</li>
     *      <li>data(//a['v' < @id])</li>
     * </ul>
     * </code>
     * @param xPathExpression
     * @return result of XPather evaluation.
     * @throws XPatherException
     */
    public Object[] evaluateXPath(String xPathExpression) throws XPatherException {
        return new XPather(xPathExpression).evaluateAgainstNode(this);
    }

    /**
     * Remove this node from the tree.
     * @return True if element is removed (if it is not root node).
     */
    public boolean removeFromTree() {
        return parent != null ? parent.removeChild(this) : false;
    }

    /**
     * Remove specified child element from this node.
     * @param child
     * @return True if child object existed in the children list.
     */
    public boolean removeChild(Object child) {
        return this.children.remove(child);
    }

    void addItemForMoving(Object item) {
    	if (itemsToMove == null) {
    		itemsToMove = new ArrayList();
    	}

    	itemsToMove.add(item);
    }

    List getItemsToMove() {
		return itemsToMove;
	}

    void setItemsToMove(List itemsToMove) {
        this.itemsToMove = itemsToMove;
    }

	boolean isFormed() {
		return isFormed;
	}

	void setFormed(boolean isFormed) {
		this.isFormed = isFormed;
	}

	void setFormed() {
		setFormed(true);
	}

    /**
     * @param autoGenerated the autoGenerated to set
     */
    public void setAutoGenerated(boolean autoGenerated) {
        this.autoGenerated = autoGenerated;
    }

    /**
     * @return the autoGenerated
     */
    public boolean isAutoGenerated() {
        return autoGenerated;
    }
    
    /**
     * @return true, if node was marked to be pruned.
     */
    public boolean isPruned() {
		return pruned;
	}

	public void setPruned(boolean pruned) {
		this.pruned = pruned;
	}
    
    public boolean isEmpty() {
        if ( !isPruned()) {
            for(Object child: this.children) {
                if(child instanceof TagNode) {
                    if (!((TagNode)child).isPruned()) {
                        return false;
                    }
                } else if( child instanceof ContentNode ) {
                    if ( !((ContentNode)child).isBlank()) {
                        return false;
                    }
                } else if ( child instanceof CommentNode) {
                    // ideally could be discarded - however standard practice is to include browser specific commands in comments. :-(
                    return false;
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    public void serialize(XmlSerializer xmlSerializer, Writer writer) throws IOException {
    	xmlSerializer.serialize(this, writer);
    }

    public TagNode makeCopy() {
    	TagNode copy = new TagNode(name);
        copy.attributes.putAll(attributes);
    	return copy;
    }
}