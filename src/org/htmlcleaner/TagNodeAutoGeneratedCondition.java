package org.htmlcleaner;

/**
 * Remove empty autogenerated nodes. These nodes are created when an unclosed tag is immediately closed.
 * @author patmoore
 *
 */
public class TagNodeAutoGeneratedCondition implements ITagNodeCondition {

    public static final TagNodeAutoGeneratedCondition INSTANCE = new TagNodeAutoGeneratedCondition();
    /**
     * @see org.htmlcleaner.ITagNodeCondition#satisfy(org.htmlcleaner.TagNode)
     */
    @Override
    public boolean satisfy(TagNode tagNode) {
        // auto-generated node that is not needed.
        return tagNode.isAutoGenerated() && tagNode.isEmpty();
    }

}