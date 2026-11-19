package github.com.gengyoubo.sscfe.interaction;

/** A synchronized player flag distinguishes stacks from other player riding features. */
public interface AxolotlStackAccess {
    boolean sscfe$isStackRider();
    void sscfe$setStackRider(boolean value);
}
