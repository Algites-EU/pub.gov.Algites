package eu.algites.pltf.modustro.builder.publication;

/** Receives optional progress updates from one publishing attempt. */
public interface AIiPublishingProgressReporter {
    /** Reports the start of an attempt. */
    void started(String aMessage);

    /** Reports determinate progress. */
    void progress(long aCompleted, long aTotal, String aUnit, String aMessage);

    /** Reports indeterminate progress. */
    void indeterminate(String aMessage);

    /** Reports normal completion of the attempt. */
    void completed(String aMessage);
}
