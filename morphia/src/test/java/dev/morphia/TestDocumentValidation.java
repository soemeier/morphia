/*
 * Copyright 2016 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.morphia;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import dev.morphia.annotations.Validation;
import dev.morphia.entities.DocumentValidation;
import dev.morphia.mapping.MappedClass;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Modify;
import dev.morphia.query.Query;
import dev.morphia.query.Update;
import dev.morphia.testmodel.User;
import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;

import static dev.morphia.query.experimental.filters.Filters.eq;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestDocumentValidation extends TestBase {
    @Test
    public void createValidation() {
        getMapper().map(DocumentValidation.class);
        getDs().enableDocumentValidation();
        assertEquals(Document.parse(DocumentValidation.class.getAnnotation(Validation.class).value()), getValidator());

        try {
            getDs().save(new DocumentValidation("John", 1, new Date()));
            fail("Document should have failed validation");
        } catch (MongoWriteException e) {
            assertTrue(e.getMessage().contains("Document failed validation"));
        }

        getDs().save(new DocumentValidation("Harold", 100, new Date()));

    }

    @Test
    public void findAndModify() {
        getMapper().map(DocumentValidation.class);
        getDs().enableDocumentValidation();

        getDs().save(new DocumentValidation("Harold", 100, new Date()));

        Query<DocumentValidation> query = getDs().find(DocumentValidation.class);
        ModifyOptions options = new FindAndModifyOptions()
                                    .bypassDocumentValidation(false);
        Modify<DocumentValidation> modify = query.modify().set("number", 5);
        try {
            modify.execute(options);
            fail("Document validation should have complained");
        } catch (MongoCommandException e) {
            // expected
        }

        options.bypassDocumentValidation(true);
        modify.execute(options);

        Assert.assertNotNull(query.filter(eq("number", 5)).iterator(new FindOptions().limit(1))
                                  .next());
    }

    @Test
    public void insert() {
        getMapper().map(DocumentValidation.class);
        getDs().enableDocumentValidation();

        try {
            getDs().insert(new DocumentValidation("Harold", 8, new Date()));
            fail("Document validation should have complained");
        } catch (MongoWriteException e) {
            // expected
        }

        getDs().insert(new DocumentValidation("Harold", 8, new Date()), new InsertOneOptions()
                                                                            .bypassDocumentValidation(true));

        Query<DocumentValidation> query = getDs().find(DocumentValidation.class)
                                                 .filter(eq("number", 8));
        Assert.assertNotNull(query.iterator(new FindOptions().limit(1)).tryNext());

        List<DocumentValidation> list = asList(new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("John", 8, new Date()),
            new DocumentValidation("Sarah", 8, new Date()),
            new DocumentValidation("Amy", 8, new Date()),
            new DocumentValidation("James", 8, new Date()));
        try {
            getDs().insert(list);
            fail("Document validation should have complained");
        } catch (MongoBulkWriteException e) {
            // expected
        }

        getDs().insert(list, new InsertManyOptions()
                                 .bypassDocumentValidation(true));

        Assert.assertTrue(query.filter(eq("number", 8)).iterator().hasNext());
    }

    @Test
    public void overwriteValidation() {
        Document validator = Document.parse("{ \"jelly\" : { \"$ne\" : \"rhubarb\" } }");
        MongoDatabase database = addValidation(validator);

        assertEquals(validator, getValidator());

        Document rhubarb = new Document("jelly", "rhubarb").append("number", 20);
        database.getCollection("validation").insertOne(new Document("jelly", "grape"));
        try {
            database.getCollection("validation").insertOne(rhubarb);
            fail("Document should have failed validation");
        } catch (MongoWriteException e) {
            assertTrue(e.getMessage().contains("Document failed validation"));
        }

        getMapper().map(DocumentValidation.class);
        getDs().enableDocumentValidation();
        assertEquals(Document.parse(DocumentValidation.class.getAnnotation(Validation.class).value()), getValidator());

        try {
            database.getCollection("validation").insertOne(rhubarb);
        } catch (MongoWriteException e) {
            assertFalse(e.getMessage().contains("Document failed validation"));
        }

        try {
            getDs().save(new DocumentValidation("John", 1, new Date()));
            fail("Document should have failed validation");
        } catch (MongoWriteException e) {
            assertTrue(e.getMessage().contains("Document failed validation"));
        }
    }

    @Test
    public void save() {
        getMapper().map(DocumentValidation.class);
        getDs().enableDocumentValidation();

        try {
            getDs().save(new DocumentValidation("Harold", 8, new Date()));
            fail("Document validation should have complained");
        } catch (MongoWriteException e) {
            // expected
        }

        getDs().save(new DocumentValidation("Harold", 8, new Date()), new InsertOneOptions()
                                                                          .bypassDocumentValidation(true));

        Query<DocumentValidation> query = getDs().find(DocumentValidation.class)
                                                 .filter(eq("number", 8));
        Assert.assertNotNull(query.iterator(new FindOptions().limit(1)).tryNext());

        List<DocumentValidation> list = asList(new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("Harold", 8, new Date()),
            new DocumentValidation("Harold", 8, new Date()));
        try {
            getDs().save(list);
            fail("Document validation should have complained");
        } catch (MongoBulkWriteException e) {
            // expected
        }

        getDs().save(list, new InsertManyOptions().bypassDocumentValidation(true));

        Assert.assertTrue(query.filter(eq("number", 8)).iterator().hasNext());
    }

    @Test
    public void testBypassDocumentValidation() {
        getMapper().map(User.class);
        getDs().enableDocumentValidation();

        final User user = new User("Jim Halpert", new Date());
        user.age = 5;

        try {
            getDs().save(user);
            fail("Document validation should have rejected the document");
        } catch (MongoWriteException ignored) {
        }

        getDs().save(user, new InsertOneOptions().bypassDocumentValidation(true));

        Assert.assertEquals(1, getDs().find(User.class).count());
    }

    @Test
    public void update() {
        getMapper().map(DocumentValidation.class);
        getDs().enableDocumentValidation();

        getDs().save(new DocumentValidation("Harold", 100, new Date()));

        Query<DocumentValidation> query = getDs().find(DocumentValidation.class);
        UpdateOptions options = new UpdateOptions()
                                    .bypassDocumentValidation(false);
        Update update = query.update().set("number", 5);
        try {
            update.execute(options);
            fail("Document validation should have complained");
        } catch (MongoWriteException e) {
            // expected
        }

        options.bypassDocumentValidation(true);
        update.execute(options);

        Assert.assertNotNull(query.filter(eq("number", 5)).iterator(new FindOptions().limit(1))
                                  .tryNext());
    }

    @Test
    public void validationDocuments() {
        Document validator = Document.parse("{ \"jelly\" : { \"$ne\" : \"rhubarb\" } }");
        getMapper().map(DocumentValidation.class);
        MappedClass mappedClass = getMapper().getMappedClass(DocumentValidation.class);

        for (ValidationLevel level : EnumSet.allOf(ValidationLevel.class)) {
            for (ValidationAction action : EnumSet.allOf(ValidationAction.class)) {
                checkValidation(validator, mappedClass, level, action);
            }
        }
    }

    private MongoDatabase addValidation(final Document validator) {
        ValidationOptions options = new ValidationOptions()
                                        .validator(validator)
                                        .validationLevel(ValidationLevel.MODERATE)
                                        .validationAction(ValidationAction.ERROR);
        MongoDatabase database = getMongoClient().getDatabase(TEST_DB_NAME);
        database.getCollection("validation").drop();
        database.createCollection("validation", new CreateCollectionOptions().validationOptions(options));
        return database;
    }

    private void checkValidation(final Document validator, final MappedClass mappedClass, final ValidationLevel level,
                                 final ValidationAction action) {
        updateValidation(mappedClass, level, action);
        Document expected = new Document("validator", validator)
                                .append("validationLevel", level.getValue())
                                .append("validationAction", action.getValue());

        Document validation = getValidation();
        for (String key : expected.keySet()) {
            assertEquals(expected.get(key), validation.get(key));
        }
    }

    @SuppressWarnings("unchecked")
    private Document getValidation() {
        Document document = getMongoClient().getDatabase(TEST_DB_NAME)
                                            .runCommand(new Document("listCollections", 1)
                                                            .append("filter", new Document("name", "validation")));

        List<Document> firstBatch = (List<Document>) ((Document) document.get("cursor")).get("firstBatch");
        return (Document) firstBatch.get(0).get("options");
    }

    private Document getValidator() {
        return (Document) getValidation().get("validator");
    }

    private void updateValidation(final MappedClass mappedClass, final ValidationLevel level, final ValidationAction action) {
        ((DatastoreImpl) getDs()).enableValidation(mappedClass, new ValidationBuilder().value("{ jelly : { $ne : 'rhubarb' } }")
                                                                                       .level(level)
                                                                                       .action(action));
    }
}
