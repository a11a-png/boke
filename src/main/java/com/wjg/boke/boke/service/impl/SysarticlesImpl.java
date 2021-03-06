package com.wjg.boke.boke.service.impl;

import com.alibaba.fastjson.JSON;
import com.wjg.boke.boke.config.esConfig;
import com.wjg.boke.boke.config.higlioghtField;
import com.wjg.boke.boke.dao.SysArticlesDao;
import com.wjg.boke.boke.dao.SysCollectionDao;
import com.wjg.boke.boke.dao.SysCommentsDao;
import com.wjg.boke.boke.po.SysArticles;
import com.wjg.boke.boke.po.SysComments;
import com.wjg.boke.boke.service.ISysarticles;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class SysarticlesImpl implements ISysarticles {

    @Resource
    public SysArticlesDao sysArticlesDao;
    @Resource
    public SysCollectionDao sysCollectionDao;
    @Autowired
    public RestHighLevelClient restHighLevelClient;
    @Resource
    public SysCommentsDao commentsDao;
    @Autowired
    public RedisTemplate redisTemplate;

    @Override
    public List<SysArticles> selectAll(Integer page, Integer limit,Integer sort_id,Integer user_id) {
        int jump=(limit-1)*page;
        return sysArticlesDao.selectAll(page, jump,sort_id,user_id);
    }

    @Override
    public Integer selectCount(Integer sortId,Integer user_id) {
        return sysArticlesDao.selectCount(sortId,user_id);
    }

    @Override
    public SysArticles selectById(Integer articlesId) {
        return sysArticlesDao.selectByPrimaryKey(articlesId);
    }

    @Override
    public List<SysArticles> selectWz(Integer page, Integer limit,Integer customerId) {
        int jump=(limit-1)*page;
        return sysArticlesDao.selectWz(page,jump,customerId);
    }

    @Override
    public Integer selectCountColl(Integer customer_id) {
        return sysCollectionDao.selectCount(customer_id);
    }

    //??????ES
    @Override
    public boolean UploadEs() throws IOException {
        //??????????????????
        List<SysArticles> listdata=sysArticlesDao.selectAll(null,null,null,null);
        //??????????????????
        BulkRequest bulkRequest=new BulkRequest();
        for (SysArticles data:listdata){
            bulkRequest.add(new IndexRequest("user_post")
                            .id(""+data.getArticlesId()+"")
                            .source(JSON.toJSONString(data), XContentType.JSON));
        }
        //??????
        BulkResponse response=restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        String str=String.valueOf(response.status());
        if (!str.equals("OK")){
            return false;
        }
        return true;
    }

    @Override
    public int update(SysArticles record) {
        return sysArticlesDao.update(record);
    }

    //ES??????
    @Override
    public List<Map<String, Object>> selectByEs(String title) throws IOException {
        SearchRequest request=new SearchRequest("user_post");
        SearchSourceBuilder searchSourceBuilder=new SearchSourceBuilder();
        //???????????????????????????????????????????????????
        searchSourceBuilder.query(QueryBuilders.multiMatchQuery(title,"articlesTitle","articlesContent","sortName","userName"));
        //??????????????????
        HighlightBuilder highfield=new HighlightBuilder();
        highfield.field("articlesTitle");
        highfield.field("articlesContent");
        highfield.field("sortName");
        highfield.field("userName");
        highfield.preTags("<em style='color:red'>");
        highfield.postTags("</em>");
        searchSourceBuilder.highlighter(highfield);
        //????????????
        request.source(searchSourceBuilder);
        //????????????
        SearchResponse response=restHighLevelClient.search(request,RequestOptions.DEFAULT);

        List<Map<String,Object>> data=new ArrayList<>();
        higlioghtField highclass=new higlioghtField();
        List<String> arrstr=new ArrayList<>();
        arrstr.add("articlesTitle");
        arrstr.add("articlesContent");
        arrstr.add("sortName");
        arrstr.add("userName");
        for (SearchHit hit:response.getHits().getHits()){
            data.add(highclass.field(hit,arrstr));
        }
        return data;
    }

    //??????
    @Override
    public boolean insert(SysArticles articles) {
        int t=sysArticlesDao.insert(articles);
        if (t>0){
            return true;
        }
        return false;
    }

    //redis???????????????
    @Override
    public void initialization() throws ParseException {
        SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy-MM-dd");
        List<String> dt=new ArrayList<>();
        for (int i=1;i<8;i++){
            int time=i*86400000;
            Long datetime=new Date().getTime()-time;
            dt.add(dateFormat.format(datetime));
            redisTemplate.delete(dateFormat.format(datetime));
        }
        String now= dateFormat.format(new Date());
        //1?????????7???????????????
        List<SysComments> comments=commentsDao.selectBydate(dt.get(6),now);
        //2????????????????????????????????????
        for (SysComments a:comments){
            String id=String.valueOf(a.getArticleId());
            String dy=dateFormat.format(a.getCommentDate());
            redisTemplate.delete(id);
            //????????????????????????????????????
            if (!redisTemplate.hasKey(dy)){
                //??????????????????, ??????????????????7???
                redisTemplate.boundZSetOps(dateFormat.format(a.getCommentDate())).expire(7,TimeUnit.DAYS);
                //???????????????????????????7??????????????????
                redisTemplate.boundZSetOps(dateFormat.format(a.getCommentDate())).add(id,1);
            }else{
                Set<String> setlist=redisTemplate.boundZSetOps(dy).range(0,-1);
                if (setlist.size()>0){
                    for (String b:setlist) {
                        //???????????????????????????????????????????????????
                        if (!b.equals(id)){
                            //???????????????????????????7??????????????????
                            redisTemplate.boundZSetOps(dateFormat.format(a.getCommentDate())).add(id,1);
                        }else{
                            //???????????????1
                            redisTemplate.boundZSetOps(dateFormat.format(a.getCommentDate())).incrementScore(id,1);
                        }
                    }
                }else{
                    redisTemplate.boundZSetOps(dateFormat.format(a.getCommentDate())).add(id,1);
                }
            }
        }
        //3???????????????
        redisTemplate.opsForZSet().unionAndStore(now,dt,"count");
        //?????????10?????????
        Set<String> socr=redisTemplate.boundZSetOps("count").reverseRange(0,9);
        for (String z:socr){
            //?????????10?????????????????????
            SysArticles articles=sysArticlesDao.selectByPrimaryKey(Integer.valueOf(z));
            //????????????,????????????????????? 7???
            redisTemplate.boundValueOps(z).set(articles,7, TimeUnit.DAYS);
        }
    }

    //????????????
    public void redissave(String key,SysArticles articles){
         //?????????????????????
         if (!redisTemplate.hasKey(key)){
             //??????????????????,????????????????????? 7???
             redisTemplate.boundValueOps(key).set(articles,7, TimeUnit.DAYS);
         }
    }

}
