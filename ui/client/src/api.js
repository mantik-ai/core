import axios from 'axios'
import VueAxios from 'vue-axios'

// Axios Base Config
const baseConfig = {
    baseURL: "/api"
}

const ApiService = {
    init(vueApp) {
        vueApp.use(VueAxios, axios);
        this.axios = vueApp.axios;
    },
    /** Fetches current version, see VersionResponse in Scala */
    version(){
        return this.axios.get("version", baseConfig);
    },
    /** Fetches current job list, see JobsResponse in Scala. */
    jobs(){
        return this.axios.get("jobs", baseConfig);
    },
    /** Poll the list of jobs with the last version, see JobsResponse. Comes back
     * as soon as something changed or after a sensible amount of time. */
    pollJobs(version){
        return this.axios.get(`jobs?poll=${version}`, baseConfig);
    },
    /** Gives information about a job. See JobResponse. */
    job(jobId) {
        return this.axios.get(`jobs/${jobId}`, baseConfig);
    },
    /** Poll a job for changes, see JobResponse. */
    pollJob(jobId, version){
        return this.axios.get(  `jobs/${jobId}?poll=${version}`, baseConfig);
    },
    /** Returns an operation graph. */
    graph(jobId, operationId) {
        return this.axios.get(`jobs/${jobId}/operations/${operationId}/graph`, baseConfig)
    },
    /** Polls a graph. */
    pollGraph(jobId, operationId, version){
        return this.axios.get(`jobs/${jobId}/operations/${operationId}/graph?poll=${version}`, baseConfig)
    }
}

export default ApiService;
