package io.vertx.web;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.List;
import java.util.stream.Collectors;

public class Verticle extends AbstractVerticle {

    // private Map<Integer, Whisky> products = new LinkedHashMap<>();
    private JDBCClient _dbClient;

    @Override
    public void start(Future<Void> fut) {

        _dbClient = JDBCClient.createShared(vertx, config(), "My-Whisky-Collection");

        startBackend((connection) -> createSomeData(connection,
                (nothing) -> startWebApp((http) -> completeStartup(http, fut)), fut), fut);

    }

    private void startBackend(Handler<AsyncResult<SQLConnection>> next, Future<Void> fut) {
        _dbClient.getConnection(ar -> {
            if (ar.failed()) {
                fut.fail(ar.cause());
            } else {
                next.handle(Future.succeededFuture(ar.result()));
            }
        });
    }

    private void startWebApp(Handler<AsyncResult<HttpServer>> next) {
        // createSomeData();

        Router router = Router.router(vertx);

        router.route("/").handler(routingContext -> {
            HttpServerResponse response = routingContext.response();
            response.putHeader("content-type", "text/html").end("<h1>Hello from my first Vert.x 3 application</h1>");
        });

        router.route("/assets/*").handler(StaticHandler.create("assets"));

        router.get("/api/whiskies").handler(this::getAll);
        router.route("/api/whiskies*").handler(BodyHandler.create());
        router.post("/api/whiskies").handler(this::addOne);
        router.get("/api/whiskies/:id").handler(this::getOne);
        router.put("/api/whiskies/:id").handler(this::updateOne);
        router.delete("/api/whiskies/:id").handler(this::deleteOne);

        vertx.createHttpServer().requestHandler(router::accept).listen(config().getInteger("http.port", 9999),
                next::handle);

        // vertx.createHttpServer().requestHandler(router::accept).listen(config().getInteger("http.port",
        // 9999),
        // result -> {
        // if (result.succeeded()) {
        // fut.complete();
        // } else {
        // fut.fail(result.cause());
        // }
        // });
    }

    private void completeStartup(AsyncResult<HttpServer> http, Future<Void> fut) {
        if (http.succeeded()) {
            fut.complete();
        } else {
            fut.fail(http.cause());
        }
    }

    @Override
    public void stop() throws Exception {
        _dbClient.close();
    }

    private void addOne(RoutingContext routingContext) {
        _dbClient.getConnection(ar -> {
            final Whisky whisky = Json.decodeValue(routingContext.getBodyAsString(), Whisky.class);
            SQLConnection connection = ar.result();
            insert(whisky, connection, (r) -> routingContext.response().setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8").end(Json.encodePrettily(r.result())));
            connection.close();
        });

        // final Whisky whisky = Json.decodeValue(routingContext.getBodyAsString(),
        // Whisky.class);
        // products.put(whisky.getId(), whisky);

        // routingContext.response().setStatusCode(201).putHeader("content-type",
        // "application/json; charset=utf-8")
        // .end(Json.encodePrettily(whisky));
    }

    private void getOne(RoutingContext routingContext) {
        final String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            _dbClient.getConnection(ar -> {
                // Read the request's content and create an instance of Whisky.
                SQLConnection connection = ar.result();
                select(id, connection, result -> {
                    if (result.succeeded()) {
                        routingContext.response().setStatusCode(200)
                                .putHeader("content-type", "application/json; charset=utf-8")
                                .end(Json.encodePrettily(result.result()));
                    } else {
                        routingContext.response().setStatusCode(404).end();
                    }
                    connection.close();
                });
            });
        }

        // final String id = routingContext.request().getParam("id");
        // if (id == null) {
        // routingContext.response().setStatusCode(400).end();
        // } else {
        // final Integer idAsInteger = Integer.valueOf(id);
        // Whisky whisky = products.get(idAsInteger);
        // if (whisky == null) {
        // routingContext.response().setStatusCode(404).end();
        // } else {
        // routingContext.response().putHeader("content-type", "application/json;
        // charset=utf-8")
        // .end(Json.encodePrettily(whisky));
        // }
        // }
    }

    private void updateOne(RoutingContext routingContext) {
        final Integer id = Integer.parseInt(routingContext.request().getParam("id"));
        JsonObject json = routingContext.getBodyAsJson();
        if (id == null || json == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            _dbClient.getConnection(ar -> update(id, json, ar.result(), (whisky) -> {
                if (whisky.failed()) {
                    routingContext.response().setStatusCode(404).end();
                } else {
                    routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
                            .end(Json.encodePrettily(whisky.result()));
                }
                ar.result().close();
            }));
        }

        // final String id = routingContext.request().getParam("id");
        // JsonObject json = routingContext.getBodyAsJson();
        // if (id == null || json == null) {
        // routingContext.response().setStatusCode(400).end();
        // } else {
        // final Integer idAsInteger = Integer.valueOf(id);
        // Whisky whisky = products.get(idAsInteger);
        // if (whisky == null) {
        // routingContext.response().setStatusCode(404).end();
        // } else {
        // whisky.setName(json.getString("name"));
        // whisky.setOrigin(json.getString("origin"));
        // routingContext.response().putHeader("content-type", "application/json;
        // charset=utf-8")
        // .end(Json.encodePrettily(whisky));
        // }
        // }
    }

    private void deleteOne(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        if (id == null) {
            routingContext.response().setStatusCode(400).end();
        } else {
            _dbClient.getConnection(ar -> {
                SQLConnection connection = ar.result();
                connection.execute("DELETE FROM Whisky WHERE id='" + id + "'", result -> {
                    routingContext.response().setStatusCode(204).end();
                    connection.close();
                });
            });
        }
        // String id = routingContext.request().getParam("id");
        // if (id == null) {
        // routingContext.response().setStatusCode(400).end();
        // } else {
        // Integer idAsInteger = Integer.valueOf(id);
        // products.remove(idAsInteger);
        // }
        // routingContext.response().setStatusCode(204).end();
    }

    private void getAll(RoutingContext routingContext) {
        _dbClient.getConnection(ar -> {
            SQLConnection connection = ar.result();
            connection.query("SELECT * FROM public.Whisky", result -> {
                List<Whisky> whiskies = result.result().getRows().stream().map(Whisky::new)
                        .collect(Collectors.toList());
                routingContext.response().putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(whiskies));
                connection.close();
            });
        });
        // routingContext.response().putHeader("content-type", "application/json;
        // charset=utf-8")
        // .end(Json.encodePrettily(products.values()));
    }

    private void createSomeData(AsyncResult<SQLConnection> result, Handler<AsyncResult<Void>> next, Future<Void> fut) {
        if (result.failed()) {
            fut.fail(result.cause());
        } else {
            SQLConnection connection = result.result();
            connection.execute("CREATE TABLE IF NOT EXISTS Whisky (id SERIAL PRIMARY KEY, name varchar(100), "
                    + "origin varchar(100))", ar -> {
                        if (ar.failed()) {
                            fut.fail(ar.cause());
                            connection.close();
                            return;
                        }
                        connection.query("SELECT * FROM Whisky", select -> {
                            if (select.failed()) {
                                fut.fail(ar.cause());
                                connection.close();
                                return;
                            }
                            if (select.result().getNumRows() == 0) {
                                insert(new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay"), connection,
                                        (v) -> insert(new Whisky("Talisker 57° North", "Scotland, Island"), connection,
                                                (r) -> {
                                                    next.handle(Future.<Void>succeededFuture());
                                                    connection.close();
                                                }));
                            } else {
                                next.handle(Future.<Void>succeededFuture());
                                connection.close();
                            }
                        });
                    });
        }
    }

    private void insert(Whisky whisky, SQLConnection connection, Handler<AsyncResult<Whisky>> next) {
        String sql = "INSERT INTO public.Whisky (name, origin) VALUES (?, ?)";
        connection.updateWithParams(sql, new JsonArray().add(whisky.getName()).add(whisky.getOrigin()), (ar) -> {
            if (ar.failed()) {
                next.handle(Future.failedFuture(ar.cause()));
                return;
            }
            UpdateResult result = ar.result();
            Whisky w = new Whisky(result.getKeys().getInteger(0), whisky.getName(), whisky.getOrigin());
            next.handle(Future.succeededFuture(w));
        });
    }

    private void select(String id, SQLConnection connection, Handler<AsyncResult<Whisky>> resultHandler) {
        connection.queryWithParams("SELECT * FROM public.Whisky WHERE id=?", new JsonArray().add(id), ar -> {
            if (ar.failed()) {
                resultHandler.handle(Future.failedFuture("Whisky not found"));
            } else {
                if (ar.result().getNumRows() >= 1) {
                    resultHandler.handle(Future.succeededFuture(new Whisky(ar.result().getRows().get(0))));
                } else {
                    resultHandler.handle(Future.failedFuture("Whisky not found"));
                }
            }
        });
    }

    private void update(Integer id, JsonObject content, SQLConnection connection,
            Handler<AsyncResult<Whisky>> resultHandler) {
        String sql = "UPDATE public.Whisky SET name=?, origin=? WHERE id=?";
        connection.updateWithParams(sql,
                new JsonArray().add(content.getString("name")).add(content.getString("origin")).add(id), update -> {
                    if (update.failed()) {
                        resultHandler.handle(Future.failedFuture("Cannot update the whisky"));
                        return;
                    }
                    if (update.result().getUpdated() == 0) {
                        resultHandler.handle(Future.failedFuture("Whisky not found"));
                        return;
                    }
                    resultHandler.handle(Future.succeededFuture(
                            new Whisky(Integer.valueOf(id), content.getString("name"), content.getString("origin"))));
                });
    }

    // private void createSomeData() {
    // Whisky bowmore = new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay");
    // products.put(bowmore.getId(), bowmore);
    // Whisky talisker = new Whisky("Talisker 57° North", "Scotland, Island");
    // products.put(talisker.getId(), talisker);
    // }
}
