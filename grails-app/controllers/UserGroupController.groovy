/*************************************************************************
 * tranSMART - translational medicine data mart
 * 
 * Copyright 2008-2012 Janssen Research & Development, LLC.
 * 
 * This product includes software developed at Janssen Research & Development, LLC.
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License 
 * as published by the Free Software  * Foundation, either version 3 of the License, or (at your option) any later version, along with the following terms:
 * 1.	You may convey a work based on this program in accordance with section 5, provided that you retain the above notices.
 * 2.	You may convey verbatim copies of this program code as you receive it, in any medium, provided that you retain the above notices.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS    * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 *
 ******************************************************************/
import auth.AuthUser
import auth.Principal
import auth.UserGroup
import command.UserGroupCommand
import grails.converters.JSON
import groovy.sql.Sql

class UserGroupController {
    def dataSource;


    def index = { redirect(action: 'list', params: params) }

    // the delete, save and update actions only accept POST requests
    def allowedMethods = [delete:'POST', save:'POST', update:'POST']

    def list = {
        if(!params.max) {
            params.max = 10
        }
        [ userGroupInstanceList: UserGroup.findAllByIdGreaterThanEquals(0, params ) ]
    }

    def membership = {

    }

    def show = {
        def userGroupInstance = UserGroup.get( params.id )

        if(!userGroupInstance) {
            flash.message = "UserGroup not found with id ${params.id}"
            redirect(action:'list')
        } else {
            return [userGroupInstance: userGroupInstance]
        }
    }

    def delete = {
        def userGroupInstance = UserGroup.get( params.id )
        if(userGroupInstance) {
            userGroupInstance.delete()
            flash.message = "UserGroup ${params.id} deleted"
            redirect(action:'list')
        } else {
            flash.message = "UserGroup not found with id ${params.id}"
            redirect(action:'list')
        }
    }

    def edit = {
        def userGroupInstance = UserGroup.get( params.id )

        if(!userGroupInstance) {
            flash.message = "UserGroup not found with id ${params.id}"
            redirect(action:'list')
        } else {
            return [ userGroupInstance : userGroupInstance ]
        }
    }

    def update = {
        def userGroupInstance = UserGroup.get( params.id )
        if(userGroupInstance) {
            userGroupInstance.properties = params
            if(!userGroupInstance.hasErrors() && userGroupInstance.save()) {
                flash.message = "UserGroup ${params.id} updated"
                redirect(action:'show',id:userGroupInstance.id)
            } else {
                render(view:'edit',model:[userGroupInstance:userGroupInstance])
            }
        } else {
            flash.message = "UserGroup not found with id ${params.id}"
            redirect(action:'edit',id:params.id)
        }
    }

    def create = {
        def userGroupInstance = new UserGroup()
        userGroupInstance.properties = params
        return ['userGroupInstance':userGroupInstance]
    }

    def save = {
        def userGroupInstance = new UserGroup(params)

        // need to get id from sequence - we have to do this since the principal id can not
        // be generated by sequence -- look into WWID in the authUser....
        def sql = new Sql(dataSource);
        def seqSQL = "SELECT HIBERNATE_SEQUENCE.nextval FROM DUAL";
        def result = sql.firstRow(seqSQL);
        println(result)

        userGroupInstance.id = result.nextval;

            if (!userGroupInstance.hasErrors() && userGroupInstance.save()) {
            flash.message = "UserGroup ${userGroupInstance.id} created"
            redirect(action: 'show', id: userGroupInstance.id)
        } else {
            render(view: 'create', model: [userGroupInstance: userGroupInstance])
        }
            }


    def ajaxGetUserSearchBoxData =
        {
        def userData = AuthUser.withCriteria {
            // TODO: searchText is not escaped for like special characters.
            // This is not trivial to do in a database agnostic way afaik, see:
            // http://git.io/H9y7gQ
            or {
                ilike 'name', "%${params.query}%"
                ilike 'username', "%${params.query}%"
            }
        }.collect { AuthUser user ->
            [name: user.name, username :user.username, type: user.type,
                    description: user.description, uid: user.id]
			}
        def result = [rows: userData]
        render text: params.callback + "(" + (result as JSON) + ")",
                contentType:"application/javascript"
    }



    def searchUsersNotInGroup =
        {
    	def userGroupInstance = UserGroup.get( params.id )
    	def groupid=Long.parseLong(params.id);
    	def searchtext=params.searchtext;
    	def users=searchForUsersNotInGroup(groupid, searchtext);
            render(template: 'addremove', model: [userGroupInstance: userGroupInstance, usersToAdd: users])
   }
    def searchGroupsWithoutUser =
        {
    	def userInstance = AuthUser.get( params.id )
    	def searchtext=params.searchtext;
    	def groupswithuser=getGroupsWithUser(userInstance.id);
    	def groupswithoutuser=getGroupsWithoutUser(userInstance.id, searchtext);
    	render(template:'addremoveg',model:[userInstance: userInstance, groupswithuser: groupswithuser, groupswithoutuser: groupswithoutuser])
   }


    def addUserToGroups =
        { UserGroupCommand fl ->
        def userInstance = AuthUser.get params.currentprincipalid
        def groupsToAdd = UserGroup.findAllByIdInList fl.groupstoadd.collect { it.toLong() }
        if(userInstance) {
                groupsToAdd.each { g ->
                g.addToMembers userInstance
                g.save failOnError: true, flush: true
            }
        }

        def searchText = params.searchtext

        def groupsWithUser = getGroupsWithUser userInstance.id
        def groupsWithoutUser = getGroupsWithoutUser userInstance.id, searchText

        render template: 'addremoveg',
                model: [ userInstance: userInstance,
                        groupswithuser: groupsWithUser,
                        groupswithoutuser: groupsWithoutUser]
    }


    def removeUserFromGroups =
        { UserGroupCommand fl ->
        def userInstance = AuthUser.get params.currentprincipalid
        def groupsToRemove = UserGroup.findAllByIdInList fl.groupstoremove.collect { it.toLong() }
        if(userInstance) {
                groupsToRemove.each { g ->
                g.removeFromMembers userInstance
                g.save failOnError: true, flush: true
            }
        }

        def searchText = params.searchtext

        def groupsWithUser = getGroupsWithUser userInstance.id
        def groupsWithoutUser = getGroupsWithoutUser userInstance.id, searchText

        render template: 'addremoveg',
                model: [ userInstance: userInstance,
                        groupswithuser: groupsWithUser,
                        groupswithoutuser: groupsWithoutUser]
    }

    def addUsersToUserGroup =
        { UserGroupCommand fl ->

            println("INCOMOING to add Group:" + params.userstoadd);
    		def userGroupInstance = UserGroup.get( params.id )
    		fl.userstoadd.collect{println("collecting:"+it.toLong())};
    		def usersToAdd = AuthUser.findAll("from AuthUser r where r.id in (:p)", [p:fl.userstoadd.collect{it.toLong()}]);
            if(userGroupInstance) {
                if(params.version) {
                    def version = params.version.toLong()
                    if(userGroupInstance.version > version) {

                        userGroupInstance.errors.rejectValue("version", "userGroup.optimistic.locking.failure", "Another user has updated this UserGroup while you were editing.")
                        render(template:'addremove',model:[userGroupInstance:userGroupInstance])
                    }
                }
                usersToAdd.each { r ->
                    userGroupInstance.members.add(r);
                	println("Adding user:"+r.id);
                };

                if(!userGroupInstance.hasErrors() && userGroupInstance.save(flush:true)) {
                    flash.message = "UserGroup ${params.id} updated"
                    	render(template:'addremove',model:[userGroupInstance:userGroupInstance, usersToAdd: searchForUsersNotInGroup(params.id.toLong(), fl.searchtext) ])
                } else {
                    render(template: 'addremove', model: [userGroupInstance: userGroupInstance, , usersToAdd: searchForUsersNotInGroup(params.id.toLong(), fl.searchtext)])
                }
            } else {
                flash.message = "UserGroup not found with id ${params.id}"
                	render(template:'addremove',model:[userGroupInstance:userGroupInstance, usersToAdd: searchForUsersNotInGroup(params.id.toLong(), fl.searchtext) ])
            }
    }


    def removeUsersFromUserGroup =
        {
    		UserGroupCommand fl ->
    		def userGroupInstance = UserGroup.get( params.id )
                if (!fl.hasErrors()) {
                    println("no errors")
                }
    		fl.errors.allErrors.each {
		        println it
		    }
    		fl.userstoremove.collect{println("collecting:"+it.toLong())};
    		def usersToRemove = AuthUser.findAll("from AuthUser r where r.id in (:p)", [p:fl.userstoremove.collect{it.toLong()}]);
            if(userGroupInstance) {
                if(params.version) {
                    def version = params.version.toLong()
                    if(userGroupInstance.version > version) {

                        userGroupInstance.errors.rejectValue("version", "userGroup.optimistic.locking.failure", "Another user has updated this userGroup while you were editing.")
                        render(template:'addremove',model:[userGroupInstance:userGroupInstance])
                    }
                }
                    usersToRemove.each { r ->
                        userGroupInstance.members.remove(r);
                	println("Removing user:"+r.id);
                };
                if(!userGroupInstance.hasErrors() && userGroupInstance.save(flush:true)) {
                    flash.message = "UserGroup ${params.id} updated"
                    	render(template:'addremove',model:[userGroupInstance:userGroupInstance, usersToAdd: searchForUsersNotInGroup(params.id.toLong(), fl.searchtext) ])
                    } else {
                        render(template: 'addremove', model: [userGroupInstance: userGroupInstance, , usersToAdd: searchForUsersNotInGroup(params.id.toLong(), fl.searchtext)])
                }
                } else {
                flash.message = "UserGroup not found with id ${params.id}"
                	render(template:'addremove',model:[userGroupInstance:userGroupInstance, usersToAdd: searchForUsersNotInGroup(params.id.toLong(), fl.searchtext) ])
            }
    }


    def searchForUsersNotInGroup(groupid, insearchtext) {
      	def searchtext='%'+insearchtext.toString().toUpperCase()+'%';
      	return AuthUser.executeQuery('from AuthUser us WHERE us NOT IN (select u.id from UserGroup g, IN (g.members) u where g.id=?) AND upper(us.name) LIKE ? ORDER BY us.userRealName',[groupid, searchtext]).sort{it.name};
    }

    def ajaxGetUsersAndGroupsSearchBoxData =
        {
	    	String searchText = request.getParameter("query");
			def userdata=[];
			 def users=Principal.executeQuery("from Principal p where upper(p.name) like upper ('%"+searchText+"%') order by p.name");
				users.each{user ->

                if (user.type == 'USER') {
						userdata.add([name:user.name, username:user.username,  type:user.type, description:user.description, uid:user.id ])
                } else {
						userdata.add([name:user.name, username:"No Login", type:user.type, description:user.description, uid:user.id ])
						}
					}
			def result = [rows:userdata]
            //println(result as JSON)
            render params.callback + "(" + (result as JSON) + ")"
	    }
    

    def getGroupsWithUser(userid) {
        return UserGroup.executeQuery('Select g FROM UserGroup g, IN (g.members) m WHERE m.id=?)', userid);
	}

    def getGroupsWithoutUser(userid, insearchtext) {
		def searchtext='%'+insearchtext.toString().toUpperCase()+'%'
		return UserGroup.executeQuery('from UserGroup g WHERE g.id<>-1 AND g.id NOT IN (SELECT g2.id from UserGroup g2, IN (g2.members) m WHERE m.id=?) AND upper(g.name) like ?', [userid, searchtext] );
	} 
}
