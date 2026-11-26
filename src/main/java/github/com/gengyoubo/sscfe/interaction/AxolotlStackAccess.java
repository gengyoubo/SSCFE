package github.com.gengyoubo.sscfe.interaction;

/**
 * A synchronized per-rider link mode distinguishes axolotl groups from other riding features.
 * The rider stores how it is attached to the player it rides.
 */
public interface AxolotlStackAccess {
    byte NONE = 0;
    /** Lies on the back of the player below (pile). */
    byte STACK = 1;
    /** Holds the tail of the player in front and is dragged along the ground (chain). */
    byte CHAIN = 2;

    byte sscfe$linkMode();
    void sscfe$setLinkMode(byte mode);

    default boolean sscfe$isStackRider() { return sscfe$linkMode() == STACK; }
    default boolean sscfe$isChainRider() { return sscfe$linkMode() == CHAIN; }
    default boolean sscfe$isLinkRider() { return sscfe$linkMode() != NONE; }
}
