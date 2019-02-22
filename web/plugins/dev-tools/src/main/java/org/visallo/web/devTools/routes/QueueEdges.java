package org.visallo.web.devTools.routes;

import com.google.inject.Inject;
import org.visallo.core.model.user.AuthorizationRepository;
import org.visallo.webster.annotations.Handle;
import org.visallo.webster.annotations.Optional;
import org.visallo.webster.annotations.Required;
import org.visallo.webster.ParameterizedHandler;
import org.vertexium.Authorizations;
import org.vertexium.Edge;
import org.vertexium.Graph;
import org.visallo.core.model.user.UserRepository;
import org.visallo.core.model.workQueue.Priority;
import org.visallo.core.model.workQueue.WorkQueueRepository;
import org.visallo.core.util.VisalloLogger;
import org.visallo.core.util.VisalloLoggerFactory;
import org.visallo.web.VisalloResponse;
import org.visallo.web.clientapi.model.ClientApiSuccess;

public class QueueEdges implements ParameterizedHandler {
    private static final VisalloLogger LOGGER = VisalloLoggerFactory.getLogger(QueueEdges.class);
    private final UserRepository userRepository;
    private final AuthorizationRepository authorizationRepository;
    private final Graph graph;
    private final WorkQueueRepository workQueueRepository;

    @Inject
    public QueueEdges(
            UserRepository userRepository,
            AuthorizationRepository authorizationRepository, Graph graph,
            WorkQueueRepository workQueueRepository
    ) {
        this.userRepository = userRepository;
        this.authorizationRepository = authorizationRepository;
        this.graph = graph;
        this.workQueueRepository = workQueueRepository;
    }

    @Handle
    public ClientApiSuccess handle(
            @Required(name = "priority") String priorityString,
            @Optional(name = "label") String label
    ) throws Exception {
        final Priority priority = Priority.safeParse(priorityString);
        if (label != null && label.trim().length() == 0) {
            label = null;
        }
        //final Authorizations authorizations = userRepository.getAuthorizations(userRepository.getSystemUser());
        final Authorizations authorizations = authorizationRepository.getGraphAuthorizations(userRepository.getSystemUser());

        final String finalLabel = label;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                LOGGER.info("requeue all edges");
                Iterable<Edge> edges = graph.getEdges(authorizations);
                for (Edge edge : edges) {
                    if (finalLabel != null && !finalLabel.equals(edge.getLabel())) {
                        continue;
                    }
                    workQueueRepository.pushElement(edge, priority);
                }
                workQueueRepository.flush();
                LOGGER.info("requeue all edges complete");
            }
        });
        t.setName("requeue-edges");
        t.start();

        return VisalloResponse.SUCCESS;
    }
}
