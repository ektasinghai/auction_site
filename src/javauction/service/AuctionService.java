package javauction.service;

import javauction.model.AuctionEntity;
import javauction.model.CategoryEntity;
import javauction.util.HibernateUtil;
import org.hibernate.*;
import org.hibernate.criterion.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

/**
 * Created by gpelelis on 5/7/2016.
 */
public class AuctionService extends Service {

    public AuctionEntity getAuction(Object obj) {
        Session session = HibernateUtil.getSession();
        try {
            AuctionEntity auction = null;
            if (obj instanceof String) {
                String auction_name = obj.toString();
                Query query = session.createQuery("from AuctionEntity where name = :auction_name");
                List results = query.setParameter("auction_name", auction_name).list();
                if (results.size() > 0) {
                    auction = (AuctionEntity) results.get(0);
                }
            } else if (obj instanceof Long) {
                long aid = (long) obj;
                auction = (AuctionEntity) session.get(AuctionEntity.class, aid);
            }
            return auction;
        } catch (HibernateException e) {
            e.printStackTrace();
        } finally {
            try {
                if (session != null) session.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    public List getAllEndedAuctions(Long uid, boolean isSeller) {
        Session session = HibernateUtil.getSession();
        Transaction tx = null;
        List auctions = null;
        try {
            tx = session.beginTransaction();
            Criteria criteria = session.createCriteria(AuctionEntity.class);
            /* get all inactive */
            criteria.add(Restrictions.eq("isStarted", (byte) 0));
            /* get all those that are really ended */
            Timestamp currentDate = new Timestamp(Calendar.getInstance().getTimeInMillis());
            criteria.add(Restrictions.lt("endingDate", currentDate));
            /* where seller id == sid */
            if (uid != null) {
                if (isSeller) {
                    criteria.add(Restrictions.eq("sellerId", uid));
                } else {
                    criteria.add(Restrictions.eq("buyerId", uid));
                }
            }
            criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
            auctions = criteria.list();
            tx.commit();
        } catch (HibernateException e) {
            if (tx != null) {
                tx.rollback();
            }
            e.printStackTrace();
        } finally {
            try {
                if (session != null) session.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return auctions;
    }

    public List getAllAuctions(long sid, boolean getAllActive) {
        Session session = HibernateUtil.getSession();
        List results = null;
        try {
            Query query;
            if (getAllActive) {
                query = session.createQuery("from AuctionEntity where isStarted = 1");
            } else {
                query = session.createQuery("from AuctionEntity where sellerId = :sid");
                query.setParameter("sid", sid);
            }
            results = query.list();
        } catch (HibernateException e) {
            e.printStackTrace();
        } finally {
            try {
                if (session != null) session.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return results;
    }

    /* simple search: search for auctions whose names contain string name */
    public List searchAuction(String name, int page) {
        Session session = HibernateUtil.getSession();
        Transaction tx = null;
        List auctions = null;
        int pagesize = 6;
        int start = pagesize*page;
        try {
            tx = session.beginTransaction();
            Criteria criteria = session.createCriteria(AuctionEntity.class);
            /* stuff for pagination */
            criteria.addOrder(Order.desc("name"));
            criteria.setFirstResult(start); // 0, pagesize*1 + 1, pagesize*2 + 1, ...
            criteria.setMaxResults(pagesize);
            criteria.setFetchMode("categories", FetchMode.SELECT);  // disabling those "FetchMode.SELECT"
            criteria.setFetchMode("bids", FetchMode.SELECT);        // will screw up everything.
            criteria.setFetchMode("seller", FetchMode.SELECT);
            criteria.setFetchMode("images", FetchMode.SELECT);
            /* based on name */
            criteria.add(Restrictions.like("name", name, MatchMode.ANYWHERE));
            /* only active */
            criteria.add(Restrictions.eq("isStarted", (byte) 1));
            /* get all those that are really not ended */
            Timestamp currentDate = new Timestamp(Calendar.getInstance().getTimeInMillis());
            criteria.add(Restrictions.gt("endingDate", currentDate));


            criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
            auctions = criteria.list();

            tx.commit();
        } catch (HibernateException e) {
            if (tx != null) {
                tx.rollback();
            }
            e.printStackTrace();
        } finally {
            try {
                if (session != null) session.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return auctions;
    }

    /* advanced search, using custom criteria! */
    public List searchAuction(String[] categories, String desc, double minPrice, double maxPrice, String location, int page) {
        Session session = HibernateUtil.getSession();
        Transaction tx = null;
        List auctions = null;
        int pagesize = 2;
        int start = pagesize*page;
        try {
            tx = session.beginTransaction();
            Criteria criteria = session.createCriteria(AuctionEntity.class);
            criteria.addOrder(Order.desc("name"));
            criteria.setFirstResult(start); // 0, pagesize*1 + 1, pagesize*2 + 1, ...
            criteria.setMaxResults(pagesize);
            criteria.setFetchMode("categories", FetchMode.SELECT);  // disabling those fetch line
            criteria.setFetchMode("bids", FetchMode.SELECT);        // will screw up everything.
            /* only active */
            criteria.add(Restrictions.eq("isStarted", (byte) 1));
            /* get all those that are really not ended */
            Timestamp currentDate = new Timestamp(Calendar.getInstance().getTimeInMillis());
            criteria.add(Restrictions.gt("endingDate", currentDate));
            /* category search */
            if (categories != null) {
                /* convert list of strings to list of integers */
                List <Integer> intCategories = new ArrayList<>();
                for (String c : categories) {
                    intCategories.add(Integer.parseInt(c));
                }

                criteria.createAlias("categories", "auctionCategory");
                criteria.add(Restrictions.in("auctionCategory.categoryId", intCategories));
            }
            /* description search */
            if (desc != "") criteria.add(Restrictions.like("description", desc, MatchMode.ANYWHERE));
            /* minPrice < price < maxPrice */
            Criterion buyNow = Restrictions.between("buyPrice", minPrice, maxPrice);
            Criterion bid = Restrictions.between("lowestBid", minPrice, maxPrice);
            LogicalExpression bidOrBuy = Restrictions.or(buyNow, bid);
            criteria.add(bidOrBuy);

            /* location search*/
            if (location != "") criteria.add(Restrictions.like("location", location, MatchMode.ANYWHERE));

            criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
            auctions = criteria.list();
            tx.commit();
        } catch (HibernateException e) {
            if (tx != null) {
                tx.rollback();
            }
            e.printStackTrace();
        } finally {
            try {
                if (session != null) session.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return auctions;
    }

    public void activateAuction(long aid, Timestamp endingDate, boolean activate) {
        Session session = HibernateUtil.getSession();
        try {
            session.beginTransaction();
            AuctionEntity auction = (AuctionEntity) session.get(AuctionEntity.class, aid);
            if (activate) {
                auction.setIsStarted((byte) 1);
                /* when we activate the auction, we may pass the endingdate at this time */
                if (endingDate != null) auction.setEndingDate(endingDate);
            } else {
                auction.setIsStarted((byte) 0);
            }
            Timestamp timeNow = new Timestamp(Calendar.getInstance().getTimeInMillis());
            auction.setStartingDate(timeNow);

            session.update(auction);
            session.getTransaction().commit();
        } catch (HibernateException e) {
            e.printStackTrace();
        } finally {
            try {
                if (session != null) session.close();
            } catch (Exception e) {
            }
        }
    }


    // deletes an auction and all associated records of auction_has_category from db
    // todo: when we add the images, check if it also delete those
    public void deleteAuction(long aid) {
        Session session = HibernateUtil.getSession();
        Transaction transaction = null;
        try {
            transaction = session.beginTransaction();
            AuctionEntity auction = (AuctionEntity) session.get(AuctionEntity.class, aid);
            session.delete(auction);
            transaction.commit();
        } catch (HibernateException e) {
            transaction.rollback();
            e.printStackTrace();
        } finally {
            try {
                if (session != null) session.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public void updateAuction(Set<CategoryEntity> categories, Long aid, String name, String desc, Double lowestBid,
                              Double buyPrice, String location, String country, Timestamp startingDate, Timestamp endingDate, Long buyerid, Double latitude, Double longitude) {

        Session session = HibernateUtil.getSession();
        Transaction tx = null;
        AuctionEntity auction = getAuction(aid);
        try {
            tx = session.beginTransaction();
            if (categories != null) { auction.setCategories(categories); }
            if (name != null) { auction.setName(name); }
            if (desc != null) { auction.setDescription(desc); }
            if (lowestBid != null) { auction.setLowestBid(lowestBid); }
            if (buyPrice != null) { auction.setBuyPrice(buyPrice); }
            if (country != null) { auction.setCountry(country); }
            if (location != null) { auction.setLocation(location); }
            if (latitude != null) { auction.setLatitude(latitude); }
            if (longitude != null) { auction.setLongitude(longitude); }
            if (startingDate != null) { auction.setStartingDate(startingDate); }
            if (endingDate != null) { auction.setEndingDate(endingDate);}
            if (buyerid != null) { auction.setBuyerId(buyerid); }
            session.update(auction);
            tx.commit();
        } catch (HibernateException e) {
            if (tx!=null) tx.rollback();
            e.printStackTrace();
        } finally {
            try {
                if (session != null) session.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public List getEveryAuction() {
        Session session = HibernateUtil.getSession();
        List results = null;
        try {
            Query query;
            query = session.createQuery("from AuctionEntity");
            results = query.list();
        } catch (HibernateException e) {
            e.printStackTrace();
        } finally {
            try {
                if (session != null) session.close();
            } catch (Exception e) {
                // ignore
            }
        }
        return results;
    }
}
