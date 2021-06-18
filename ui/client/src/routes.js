import Home from "@/pages/Home";
import Jobs from "@/jobs/Jobs";
import Job from "@/jobs/Job";
import Version from "@/pages/Version";
import NotFound from "@/pages/NotFound";
import RunGraph from "@/jobs/RunGraph";

const routes = [
    { path: '/', component: Home },
    { path: '/jobs', component: Jobs },
    { path: '/jobs/:jobId', component: Job },
    { path: '/jobs/:jobId/operations/:operationId/graph', component: RunGraph },
    { path: '/version', component: Version },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFound }
]

export default routes;
