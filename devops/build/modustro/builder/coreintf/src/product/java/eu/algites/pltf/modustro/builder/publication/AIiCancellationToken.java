package eu.algites.pltf.modustro.builder.publication;

/** Cooperative cancellation contract for one publication attempt. */
@FunctionalInterface
public interface AIiCancellationToken {
    /** Returns true when cancellation has been requested. */
    boolean isCancellationRequested();
}
