package doser.entitydisambiguation.algorithms.collective.hybrid;

import java.util.List;

import doser.entitydisambiguation.algorithms.collective.rules.RuleAdapation;
import doser.entitydisambiguation.dpo.Response;
import doser.entitydisambiguation.knowledgebases.EntityCentricKnowledgeBaseDefault;

public class CollectiveAndContextDriver extends AlgorithmDriver {

	static final int PREPROCESSINGCONTEXTSIZE = 200;

	private Doc2Vec d2v;

	public CollectiveAndContextDriver(Response[] res, List<SurfaceForm> rep,
			EntityCentricKnowledgeBaseDefault eckb) {
		super(res, rep, eckb);
		this.d2v = new Doc2Vec(rep, PREPROCESSINGCONTEXTSIZE);
	}

	@Override
	public void solve() {
		Word2Vec w2v = new Word2Vec();
		// First candidate pruning
		CandidatePruning pruning = new CandidatePruning(w2v, d2v, eckb);
		pruning.prune(rep);
		TimeNumberDisambiguation timenumberdis = new TimeNumberDisambiguation(eckb);
		timenumberdis.solve(rep);
		LocationDisambiguation locationDis = new LocationDisambiguation(d2v,
				eckb);
		locationDis.solve(rep);
		RuleAdapation rules = new RuleAdapation(eckb, w2v);
		rules.performRuleChainBeforeCandidateSelection(rep);

		CandidateReductionW2V w2vreduction = new CandidateReductionW2V(eckb, rep, w2v);
		w2vreduction.solve();
		rep = w2vreduction.getRep();
		
		Word2VecDisambiguator simple = new Word2VecDisambiguator(
				eckb.getFeatureDefinition(), rep, w2v, true, 8, 250);
		simple.setup();
		simple.solve();
		rep = simple.getRepresentation();
		FinalEntityDisambiguation finalDis = new FinalEntityDisambiguation(
				eckb.getFeatureDefinition(), rep, w2v);
		finalDis.setup();
		finalDis.solve();
	}
}
