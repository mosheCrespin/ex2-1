package gameClient;

import Server.Game_Server_Ex2;
import api.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class MainClient  implements Runnable{
    private static window gameWindow;
    private static Arena arena;
    private static int movesCounter;

    public static void main(String[] a) {
        Thread client = new Thread(new MainClient());
        client.start();
    }

    @Override
    public void run() {
        // choose a game out of 24 games available [0,23]
        int scenario_num = 11;
        game_service game = Game_Server_Ex2.getServer(scenario_num);

        directed_weighted_graph g = json2graph(game);

        init(game);

        game.startGame();

        long sleepTime=100;

        while(game.isRunning()) {

            moveAgents(game, g);
            try {
                gameWindow.repaint();
                Thread.sleep(sleepTime);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
        double FinalScore=0;
        for(CL_Agent agent: arena.getAgents()){
            FinalScore += agent.getValue();
        }
        System.out.println("Final Score is - "+FinalScore+".");
        System.out.println( movesCounter +" moves has been made");
        System.exit(0);
    }

    private static void moveAgents(game_service game, directed_weighted_graph g) {
        //set agents
        String updatedArena = game.move();
        List<CL_Agent> agentList = arena.getAgents(updatedArena, g);
//        List<CL_Agent> agents = arena.getAgents();
        arena.setAgents(agentList);


        for(int i=0;i<agentList.size();i++) {
            CL_Agent agent = agentList.get(i);
            int id = agent.getID();
            int dest = agent.getNextNode();
            int src = agent.getSrcNode();

            if(dest==-1) {
                calculateAgentsPath(game, agent);
                dest = nextNode(g, agent);
                if(agent.path!=null){

                    game.chooseNextEdge(i, dest);

                    System.out.println("Agent: "+agent.getID()+", val: "+agent.getValue()+"   turned to node: "+agent.getNextNode());
                }
            }
        }
    }
    public static DS_DWGraph json2graph (game_service game){

        JSONObject jsonObj;
        DS_DWGraph g = new DS_DWGraph();
        try {
            jsonObj = new JSONObject(game.getGraph());
            JSONArray nodeJsonObj = jsonObj.getJSONArray("Nodes");
            JSONArray edgeJsonObj = jsonObj.getJSONArray("Edges");

            for(int i=0;i<nodeJsonObj.length();i++) {
                JSONObject node_dataObj = nodeJsonObj.getJSONObject(i);
                int key = node_dataObj.getInt("id");
                String POS = node_dataObj.getString("pos");
                String [] XY = POS.split(",");
                double x = Double.parseDouble(XY[0]);
                double y = Double.parseDouble(XY[1]);
                geoLocation pos = new geoLocation (x,y,0);
                node_data n = new nodeData(key);
                n.setLocation(pos);
                g.addNode(n);
            }

            for(int i=0;i<edgeJsonObj.length();i++) {
                JSONObject edge_dataObj = edgeJsonObj.getJSONObject(i);
                int src = edge_dataObj.getInt("src");
                int dest = edge_dataObj.getInt("dest");
                double w = edge_dataObj.getDouble("w");
                g.connect(src, dest, w);
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return g;
    }

    private static int nextNode(directed_weighted_graph g, CL_Agent agent) {
        List<node_data> path = agent.getPath();
        if(path!=null && !path.isEmpty()){
            node_data nextNode;
            if (agent.getSrcNode() == path.get(0).getKey()) {
                nextNode = path.remove(0);
            }else{
                nextNode = path.get(0);
            }
            return nextNode.getKey();
        }
        return -1;
    }

    //todo check time left?
    //TODO synchronize
    private static void calculateAgentsPath(game_service game, CL_Agent agent) {
        directed_weighted_graph g = json2graph(game);

        dw_graph_algorithms ga = new Algo_DWGraph();
        ga.init(g);

        List<CL_Pokemon> pokemons = arena.getPokemons();

        PriorityQueue<PokemonEntry> pokemonQueue = agent.getPokemonsVal();

        for (CL_Pokemon pokemon : pokemons) {

            edge_data edge = pokemon.get_edge();
            //TODO maybe check dist to dest instead
            if(edge!=null){
                double distToPokemon = ga.shortestPathDist(agent.getSrcNode(), pokemon.get_edge().getSrc());
                double huntValue = pokemon.getValue() / distToPokemon;

                pokemonQueue.add(new PokemonEntry(huntValue, pokemon));
                //TODO set agent Queue
            }
        }
        LinkedList<CL_Agent> agentsList  = new LinkedList<>();
        agentsList.add(agent);

        while(!agentsList.isEmpty()){
            //TODO check if already on the hunt
            CL_Agent iAgent = agentsList.removeFirst();
            PokemonEntry pEntry = agent.getPokemonsVal().poll();

            if (pEntry != null) {

                CL_Pokemon pokemon = pEntry.getPokemon();

                if (pokemon.persecutedBy == -1) {
                    iAgent.setPath(ga.shortestPath(iAgent.getSrcNode(), pokemon.get_edge().getSrc()), g.getNode(pokemon.get_edge().getDest()));
                } else {
                    double value = iAgent.getPokemonsVal().peek().getValue();
                    CL_Agent currAgentAfter = arena.getAgents().get(pokemon.getPersecutedBy());
                    double thisHuntValue = iAgent.getPokemonsVal().peek().getValue();
                    double otherHuntValue = currAgentAfter.getPokemonsVal().peek().getValue();
                    //if new agent have better hunt Value
                    if (otherHuntValue < thisHuntValue) {
                        //clear old
                        currAgentAfter.getPokemonsVal().poll();
                        agentsList.add(currAgentAfter);

                        //set new persecuted
                        iAgent.setPath(ga.shortestPath(iAgent.getSrcNode(), pokemon.get_edge().getSrc()), g.getNode(pokemon.get_edge().getDest()));
                    } else {
                        //if this agent have worst huntVal then sent him to another pokemon
                        iAgent.getPokemonsVal().poll();
                        //sent agent to the end of list
                        agentsList.add(iAgent);
                    }
                }

            }

        }

    }

    private static void init (game_service game){

        String pokemonString = game.getPokemons();
//        directed_weighted_graph g = game.getJava_Graph_Not_to_be_used();
        directed_weighted_graph g = json2graph(game);

        arena = new Arena();
        arena.setGraph(g);
//        arena.setPokemons(Arena.json2Pokemons(pokemonString));
        gameWindow = new window("test Ex2");
        gameWindow.setSize(1000, 700);
        gameWindow.panel.update(arena);
        gameWindow.panel.setTimeLeft(game.timeToEnd());

        gameWindow.show();
        String info = game.toString();
        JSONObject line;
        try {
            line = new JSONObject(info);
            JSONObject gameServerJ = line.getJSONObject("GameServer");
            int agentsNum = gameServerJ.getInt("agents");
            int src_node = 0;  // arbitrary node, you should start at one of the pokemon

            List<CL_Pokemon> pokemons = Arena.json2Pokemons(game.getPokemons());

            for (int i = 0; i < pokemons.size(); i++) {
                Arena.updateEdge(pokemons.get(i), g);
            }
            for (int a = 0; a < agentsNum; a++) {
                int ind = a % pokemons.size();
                CL_Pokemon c = pokemons.get(ind);
                int nn = c.get_edge().getDest();
                if (c.getType() < 0) {
                    nn = c.get_edge().getSrc();
                }
//
//            Comparator cmp = new geoLoCompPokemon();
//            CL_Pokemon minP =  Collections.min(pokemons , cmp);
//            CL_Pokemon maxp= Collections.max(pokemons , cmp);
//
//            Collections.sort(pokemons);
//            geoLocation firsAxes = new geoLocation(0, 0, 0);
//
//            double min = minP.getLocation().distance(firsAxes);
//            double max = maxp.getLocation().distance(firsAxes);
//            double rang = ((max-min)/agentsNum)+min;
//
//            ArrayList <CL_Pokemon> pStartWith = new ArrayList<>();
//
//            int i =0;
//            while (rang-min < max){
//                while(i != pokemons.size()/2) {
//                    CL_Pokemon curr = pokemons.get(i);
//                    if (curr.getLocation().distance(minP.getLocation()) < rang) {
//                        pStartWith.add(curr);
//                        rang += min;
//                        i=0;
//                    }
//                    else {
//                        i++;
//                    }
//                }
//                if (i > 0){
//                    rang += min;
//                    i=0;
//                }
//            }
//            i=0;
//            int size = pStartWith.size();
//            while (size < agentsNum){
//                CL_Pokemon curr = pokemons.get(i);
//                if (! pStartWith.contains(curr)){
//                    pStartWith.add(curr);
//                    size++;
//                }
//                i++;
//            }
//
//            for (int j = 0; j < agentsNum; j++) {
//                CL_Pokemon startDest = pStartWith.get(j);
//                int nodeStart = startDest.getSrc();
//                game.addAgent(nodeStart);
            }

                arena.setPokemons(pokemons);

            } catch(JSONException e){
                e.printStackTrace();
            }

        }
}
