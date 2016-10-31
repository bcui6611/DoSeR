package doser.entitydisambiguation.algorithms.contextcomparison;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.DefaultSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import doser.entitydisambiguation.algorithms.AbstractDisambiguationAlgorithm;
import doser.entitydisambiguation.algorithms.IllegalDisambiguationAlgorithmInputException;
import doser.entitydisambiguation.algorithms.SurfaceForm;
import doser.entitydisambiguation.algorithms.collective.dbpedia.CollectiveDisambiguationDBpediaEntities;
import doser.entitydisambiguation.backend.AbstractDisambiguationTask;
import doser.entitydisambiguation.backend.DisambiguationTaskCollective;
import doser.entitydisambiguation.dpo.DisambiguatedEntity;
import doser.entitydisambiguation.dpo.EntityDisambiguationDPO;
import doser.entitydisambiguation.dpo.Response;
import doser.entitydisambiguation.knowledgebases.AbstractKnowledgeBase;
import doser.entitydisambiguation.knowledgebases.EntityCentricKBDBpedia;
import doser.general.HelpfulMethods;
import doser.lucene.query.TermQuery;

public class SingleTFIDF extends AbstractDisambiguationAlgorithm {

	private final static Logger logger = LoggerFactory.getLogger(SingleTFIDF.class);

	private EntityCentricKBDBpedia eckb;

	private DisambiguationTaskCollective task;

	@Override
	protected boolean checkAndSetInputParameter(AbstractDisambiguationTask task) {
		AbstractKnowledgeBase kb = task.getKb();
		if (!(task instanceof DisambiguationTaskCollective)) {
			return false;
		} else if (!(kb instanceof EntityCentricKBDBpedia)) {
			return false;
		}
		this.eckb = (EntityCentricKBDBpedia) kb;
		this.task = (DisambiguationTaskCollective) task;
		return true;
	}

	@Override
	protected void processAlgorithm() throws IllegalDisambiguationAlgorithmInputException {
		List<EntityDisambiguationDPO> entityList = task.getEntityToDisambiguate();
		Response[] responseArray = new Response[entityList.size()];

		List<SurfaceForm> collectiveRep = new LinkedList<SurfaceForm>();
		for (int i = 0; i < entityList.size(); i++) {
			EntityDisambiguationDPO dpo = entityList.get(i);
			dpo.setSelectedText(dpo.getSelectedText().replaceAll("’", "'"));
			Query query = createQuery(dpo.getSelectedText(), eckb);
			final IndexSearcher searcher = eckb.getSearcher();
			final IndexReader reader = searcher.getIndexReader();
			try {
				final TopDocs top = searcher.search(query, task.getReturnNr());
				final ScoreDoc[] score = top.scoreDocs;
				if (dpo.getSelectedText().equalsIgnoreCase("") || dpo.getSelectedText() == null) {
					ArrayList<String> l = new ArrayList<String>();
					l.add("");
					SurfaceForm col = new SurfaceForm(dpo.getSelectedText(), dpo.getContext(), l, i,
							dpo.getStartPosition());
					collectiveRep.add(col);
				} else if (score.length == 1) {
					final Document doc = reader.document(score[0].doc);
					ArrayList<String> l = new ArrayList<String>();
					l.add(doc.get("Mainlink"));
					SurfaceForm col = new SurfaceForm(dpo.getSelectedText(), dpo.getContext(), l, i,
							dpo.getStartPosition());
					col.setInitial(true);
					collectiveRep.add(col);

				} else if (score.length > 1) {
					ArrayList<String> l = new ArrayList<String>();
					for (int j = 0; j < score.length; j++) {
						final Document doc = reader.document(score[j].doc);
						l.add(doc.get("Mainlink"));
					}
					SurfaceForm col = new SurfaceForm(dpo.getSelectedText(), dpo.getContext(), l, i,
							dpo.getStartPosition());
					collectiveRep.add(col);

				} else {
					ArrayList<String> l = new ArrayList<String>();
					SurfaceForm col = new SurfaceForm(dpo.getSelectedText(), dpo.getContext(), l, i,
							dpo.getStartPosition());
					collectiveRep.add(col);
				}

			} catch (final IOException e) {
				logger.error("JsonException in " + CollectiveDisambiguationDBpediaEntities.class.getName(), e);
			}
		}

		// for (SurfaceForm sf : collectiveRep) {
		// // System.out.println("SurfaceForm: "+sf.getSurfaceForm()+
		// // "Position: "+sf.getPosition());
		// List<String> l = sf.getCandidates();
		// // System.out.println("Kandidaten: ");
		// for (String s : l) {
		// // System.out.println(s);
		// }
		// // System.out.println("Ranking: ");
		// }

//		prune(collectiveRep);

		List<Response> res = Arrays.asList(disambiguate(collectiveRep, responseArray));
		task.setResponse(res);
		List<Response> reslist = task.getResponse();
		// for (Response r : reslist) {
		// List<DisambiguatedEntity> entities = r.getDisEntities();
		// System.out.println(r.getSelectedText());
		// for(DisambiguatedEntity ent : entities) {
		// System.out.println("DisambiguatedEntity: "+ent.getEntityUri());
		// }
		// }
		eckb.release();
	}

	private Response[] disambiguate(List<SurfaceForm> collectiveRep, Response[] resultList) {
		for (int i = 0; i < collectiveRep.size(); i++) {
			SurfaceForm sf = collectiveRep.get(i);
			if (sf.getCandidates().size() > 0) {
				String context = extractContext(sf.getPosition(), sf.getContext(), 1500);
//				List<String> l = sf.getCandidates();
//				Collections.shuffle(l);
//				String res = sf.getCandidates().get(0);
				String res = queryContext(context, sf.getCandidates());
				Response re = new Response();
				List<DisambiguatedEntity> entList = new LinkedList<DisambiguatedEntity>();
				DisambiguatedEntity ent = new DisambiguatedEntity();
				ent.setEntityUri(res);
				ent.setText("ToDoText");
				entList.add(ent);
				re.setDisEntities(entList);
				re.setStartPosition(-1);
				re.setSelectedText(sf.getSurfaceForm());
				resultList[i] = re;
			}
		}

		return resultList;
	}

	private String queryContext(String context, List<String> candidates) {
		String topEntity = null;
		BooleanQuery bq = new BooleanQuery();
		for (String s : candidates) {
			TermQuery tq = new TermQuery(new Term("Mainlink", s));
			bq.add(tq, Occur.SHOULD);
		}
		Filter candidateFilter = new QueryWrapperFilter(bq);

		BooleanQuery termbq = new BooleanQuery();
		final String[] split = context.split(" ");
		for (final String element : split) {
			TermQuery tq = new TermQuery(new Term("LongDescription", element));
			termbq.add(tq, Occur.SHOULD);
		}
		Query q = new FilteredQuery(termbq, candidateFilter);
		IndexSearcher s = eckb.getSearcher();
		s.setSimilarity(new BM25Similarity());
		try {
			TopDocs top = s.search(q, 100);
			ScoreDoc[] sd = top.scoreDocs;
			if (sd.length > 0) {
				for (int i = 0; i < sd.length; i++) {
					System.out.println(s.getIndexReader().document(sd[i].doc).get("Mainlink") + ": " + sd[i].score);
				}

				topEntity = s.getIndexReader().document(sd[0].doc).get("Mainlink");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return topEntity;
	}

	private Query createQuery(String sf, EntityCentricKBDBpedia kb) {
		String surfaceform = sf.toLowerCase();
		TermQuery query = new TermQuery(new Term("UniqueLabel", surfaceform));

		return query;
	}

	private void prune(List<SurfaceForm> rep) {
		for (SurfaceForm c : rep) {
			List<String> candidates = c.getCandidates();
			if (candidates.size() > 10) {
				Set<String> prunedCandidates = new HashSet<String>();

				// Sense Prior
				Map<String, Integer> map = new HashMap<String, Integer>();
				for (String candidate : candidates) {
					map.put(candidate, eckb.getFeatureDefinition().getOccurrences(c.getSurfaceForm(), candidate));
				}
				@SuppressWarnings("deprecation")
				List<Map.Entry<String, Integer>> l = HelpfulMethods.sortByValue(map);
				for (int i = 0; i < 10; ++i) {
					prunedCandidates.add(l.get(i).getKey());
					// System.out.println("SensePrior ADd: "+l.get(i).getKey()+"
					// "+l.get(i).getValue());
				}
				c.setCandidates(new ArrayList<String>(prunedCandidates));
			}
		}
	}

	@Override
	protected boolean preDisambiguation() {
		return true;
	}

}